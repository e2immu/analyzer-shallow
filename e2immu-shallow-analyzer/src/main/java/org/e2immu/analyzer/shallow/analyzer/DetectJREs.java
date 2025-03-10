package org.e2immu.analyzer.shallow.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
We expect the file ~/.e2immu.jremap to contain a property-value like mapping, as for example

Homebrew-17.0.4=openjdk-17.0.4
IBMCorporation-17.0.9=openjdk-17.0.4

it maps the actual JRE shortname as computed below to the directories in the resources of the
e2immu-shallow-aapi/src/main/resources/org/.../jdk directory.

IMPORTANT: remove the .e2immu.java_home.xml cache file when your JREs have been updated.
 */
public class DetectJREs {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectJREs.class);

    public static Map<String, String> loadJreMapping(List<ToolChain.JRE> jreList) {
        String home = System.getProperty("user.home");
        File file = new File(home + File.separator + ".e2immu.jremap");
        if (file.canRead()) {
            try (InputStream is = new FileInputStream(file)) {
                Properties properties = new Properties();
                properties.load(is);
                Map<String, String> map = properties.entrySet().stream()
                        .filter(e -> jreList.stream().anyMatch(jre -> jre.shortName().equals(e.getKey())))
                        .collect(Collectors.toUnmodifiableMap(e -> (String) e.getKey(),
                                e -> (String) e.getValue()));
                LOGGER.info("JRE mapping: {}", map);
                return map;
            } catch (IOException ioe) {
                LOGGER.error("Exception reading {}", file, ioe);
            }
        } else {
            LOGGER.warn("Cannot read {}", file);
        }
        return Map.of();
    }

    public static List<ToolChain.JRE> runSystemCommand() {
        String os = System.getProperty("os.name");
        if ("Mac OS X".equals(os)) return runSystemCommandMacOs();
        if ("Linux".equals(os)) return runSystemCommandLinux();
        throw new UnsupportedOperationException("Unrecognized operating system: " + os);
    }

    public static List<ToolChain.JRE> runSystemCommandLinux() {
        String home = System.getProperty("user.home");
        File file = new File(home + File.separator + ".e2immu.jre.properties");
        try {
            String output;
            if (!file.canRead()) {
                LOGGER.info("Calling 'update-alternatives' to find out which JREs are installed");

                Process process = new ProcessBuilder().command("update-alternatives", "--list", "java").start();
                Collect collect = new Collect(process.getInputStream());
                ExecutorService executor = Executors.newFixedThreadPool(1);
                executor.submit(collect);
                int exitCode = process.waitFor();
                if (exitCode != 0) throw new UnsupportedOperationException();
                output = collect.stringBuilder.toString();
                Files.writeString(file.toPath(), output);
            } else {
                output = Files.readString(file.toPath());
            }
            return parseLinuxOutput(output);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("Cannot detect JREs: " + e.getMessage());
        }
    }

    private static final Pattern UBUNTU = Pattern.compile("/usr/lib/jvm/java-(\\d+)-(\\S+)-(\\S+)/bin/java");

    private static List<ToolChain.JRE> parseLinuxOutput(String lines) {
        List<ToolChain.JRE> list = new ArrayList<>();
        for (String line : lines.split("\n")) {
            if (!line.isBlank()) {
                Matcher m = UBUNTU.matcher(line);
                if (m.matches()) {
                    String version = m.group(1);
                    int mainVersion = Integer.parseInt(version);
                    String shortName = m.group(3) + "-" + version;
                    ToolChain.JRE jre = new ToolChain.JRE(mainVersion, version, m.group(2), line, shortName);
                    list.add(jre);
                }
            }
        }
        return List.copyOf(list);
    }

    public static List<ToolChain.JRE> runSystemCommandMacOs() {
        String home = System.getProperty("user.home");
        File file = new File(home + File.separator + ".e2immu.java_home.xml");
        try {
            String xmlString;
            if (!file.canRead()) {
                LOGGER.info("Calling 'java_home' to find out which JREs are installed");

                Process process = new ProcessBuilder().command("/usr/libexec/java_home", "-X").start();
                Collect collect = new Collect(process.getInputStream());
                ExecutorService executor = Executors.newFixedThreadPool(1);
                executor.submit(collect);
                int exitCode = process.waitFor();
                if (exitCode != 0) throw new UnsupportedOperationException();
                xmlString = collect.stringBuilder.toString();
                Files.writeString(file.toPath(), xmlString);
            } else {
                xmlString = Files.readString(file.toPath());
            }
            return parseMacOsXml(xmlString);
        } catch (IOException | InterruptedException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("Cannot detect JREs: " + e.getMessage());
        }
    }

    private static List<ToolChain.JRE> parseMacOsXml(String xmlString) throws IOException, ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        Handler handler = new Handler();
        try (InputStream is = new ByteArrayInputStream(xmlString.getBytes())) {
            saxParser.parse(is, handler);
            List<ToolChain.JRE> jreList = List.copyOf(handler.jreList);
            LOGGER.info("Detected {} JREs using /usr/libexec/java_home: {}", jreList.size(),
                    jreList.stream().map(ToolChain.JRE::shortName).collect(Collectors.joining(", ")));
            return jreList;
        }
    }

    private static class Handler extends DefaultHandler {
        final List<ToolChain.JRE> jreList = new ArrayList<>();
        private String version;
        private String vendor;
        private String path;
        private String key;
        private StringBuilder elementValue = new StringBuilder();

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            elementValue.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("dict".equals(qName)) {
                int mainVersion = Integer.parseInt(version.substring(0, version.indexOf('.')));
                String shortName = vendor.replaceAll("[\\s.,-]", "") + "-" + version;
                jreList.add(new ToolChain.JRE(mainVersion, version, vendor, path, shortName));
                return;
            }
            String content = elementValue.toString();
            elementValue = new StringBuilder();
            if ("key".equals(qName)) {
                key = content;
            } else if ("string".equals(qName)) {
                if ("JVMPlatformVersion".equals(key)) {
                    version = content;
                } else if ("JVMVendor".equals(key)) {
                    vendor = content;
                } else if ("JVMHomePath".equals(key)) {
                    path = content;
                }
            }
        }
    }

    private static class Collect implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder stringBuilder = new StringBuilder();

        public Collect(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(line ->
                    stringBuilder.append(line).append("\n"));
        }
    }
}
