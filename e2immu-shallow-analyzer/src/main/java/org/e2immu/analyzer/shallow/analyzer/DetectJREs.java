package org.e2immu.analyzer.shallow.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/*
We expect the file ~/.e2immu.jremap to contain a property-value like mapping, as for example

Homebrew-17.0.4=openjdk-17.0.4
IBMCorporation-17.0.9=openjdk-17.0.4

it maps the actual JRE shortname as computed below to the directories in the resources of the
e2immu-shallow-aapi/src/main/resources/org/.../jdk directory.
 */
public class DetectJREs {
    private static final Logger LOGGER = LoggerFactory.getLogger(DetectJREs.class);

    public static Map<String, String> loadJreMapping(ToolChain.JRE[] jres) {
        String home = System.getProperty("user.home");
        File file = new File(home + File.separator + ".e2immu.jremap");
        if (file.canRead()) {
            try (InputStream is = new FileInputStream(file)) {
                Properties properties = new Properties();
                properties.load(is);
                Map<String, String> map = properties.entrySet().stream()
                        .filter(e -> Arrays.stream(jres).anyMatch(jre -> jre.shortName().equals(e.getKey())))
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

    public static ToolChain.JRE[] runSystemCommand() {
        LOGGER.info("Loading JREs");
        try {
            ExecutorService executor = Executors.newFixedThreadPool(1);
            Process process = new ProcessBuilder().command("/usr/libexec/java_home", "-X").start();
            Collect collect = new Collect(process.getInputStream());
            executor.submit(collect);
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new UnsupportedOperationException();
            String xmlString = collect.stringBuilder.toString();
            return parseXml(xmlString);
        } catch (IOException | InterruptedException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("Cannot detect JREs: " + e.getMessage());
        }
    }

    private static ToolChain.JRE[] parseXml(String xmlString) throws IOException, ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        Handler handler = new Handler();
        try (InputStream is = new ByteArrayInputStream(xmlString.getBytes())) {
            saxParser.parse(is, handler);
            ToolChain.JRE[] jres = handler.jreList.toArray(new ToolChain.JRE[0]);
            LOGGER.info("Detected {} JREs using /usr/libexec/java_home: {}", jres.length,
                    Arrays.stream(jres).map(ToolChain.JRE::shortName).collect(Collectors.joining(", ")));
            return jres;
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
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(stringBuilder::append);
        }
    }
}
