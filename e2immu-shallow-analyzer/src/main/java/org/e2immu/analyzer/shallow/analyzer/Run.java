package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.e2immu.analyzer.modification.prepwork.PrepAnalyzer;
import org.e2immu.language.cst.api.analysis.Message;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.util.internal.graph.G;
import org.e2immu.util.internal.graph.op.Linearize;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.e2immu.language.inspection.integration.JavaInspectorImpl.JAR_WITH_PATH_PREFIX;

public class Run {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);

    public static final String[] JRES = {
            "/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home",
            "/opt/homebrew/Cellar/openjdk@21/21.0.6/libexec/openjdk.jdk/Contents/Home",
            "/opt/homebrew/Cellar/openjdk@17/17.0.14/libexec/openjdk.jdk/Contents/Home"
    };

    public static String currentJdk() {
        String home = System.getProperty("java.home");
        return Arrays.stream(JRES).filter(home::equals).map(Run::extractJdkName).findFirst().orElseThrow();
    }

    public static final Pattern JDK_PATTERN = Pattern.compile("openjdk(@\\d+)?/([\\d.]+)/libexec/openjdk.jdk");

    public static String extractJdkName(String jdkHome) {
        Matcher m = JDK_PATTERN.matcher(jdkHome);
        if(m.find()) {
            return "openjdk-" + m.group(2);
        }
        throw new UnsupportedOperationException("Unknown JDK/JRE "+jdkHome);
    }

    public static final String[] SOURCES = {
            "../analyzer-shallow/e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"
    };

    public static void main(String[] args) throws IOException {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);
        ((Logger) LoggerFactory.getLogger("org.e2immu.analyzer.modification.prepwork")).setLevel(Level.DEBUG);

        Run run = new Run();
        for (String jre : JRES) {
            run.go(jre, SOURCES);
        }
    }

    public List<Message> go(String alternativeJreOrNull, String[] args) throws IOException {
        LOGGER.info("I'm at {}", new File(".").getAbsolutePath());
        AnnotatedApiParser annotatedApiParser = new AnnotatedApiParser();
        annotatedApiParser.initialize(alternativeJreOrNull,
                List.of("jmods/java.base.jmod", "jmods/java.xml.jmod", "jmods/java.net.http.jmod",
                        "jmods/java.datatransfer.jmod", "jmods/java.desktop.jmod",
                        JAR_WITH_PATH_PREFIX + "org/e2immu/support",
                        JAR_WITH_PATH_PREFIX + "org/slf4j",
                        JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic",
                        JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api",
                        JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi",
                        JAR_WITH_PATH_PREFIX + "org/apiguardian/api",
                        JAR_WITH_PATH_PREFIX + "org/opentest4j"
                ),
                List.of(args[0]),
                List.of("java", "javax", "e2immu", "log", "test"));
        ShallowAnalyzer shallowAnalyzer = new ShallowAnalyzer(annotatedApiParser);
        List<TypeInfo> parsedTypes = shallowAnalyzer.go();
        PrepAnalyzer prepAnalyzer = new PrepAnalyzer(annotatedApiParser.runtime());
        prepAnalyzer.initialize(annotatedApiParser.javaInspector().compiledTypesManager().typesLoaded());

        Set<Info> infos = annotatedApiParser.infos();
        LOGGER.info("Parsed and analyzed {} types; {} info objects", parsedTypes.size(), infos.size());
        infos.forEach(i -> {
            if (!i.analysis().haveAnalyzedValueFor(PropertyImpl.ANNOTATED_API)) {
                i.analysis().set(PropertyImpl.ANNOTATED_API, ValueImpl.BoolImpl.TRUE);
            }
        });

        WriteAnalysis wa = new WriteAnalysis(annotatedApiParser.runtime());
        Trie<TypeInfo> trie = new Trie<>();
        for (TypeInfo ti : parsedTypes) {
            if (ti.isPrimaryType()) {
                trie.add(ti.packageName().split("\\."), ti);
            }
        }
        File dir = new File("build/json");
        File targetFile = new File(dir, "OrgE2Immu.json");
        if (targetFile.delete()) LOGGER.debug("Deleted {}", targetFile);
        wa.write(dir.getAbsolutePath(), trie);

        WriteDecoratedAAPI writeDecoratedAAPI = new WriteDecoratedAAPI(annotatedApiParser.runtime());
        writeDecoratedAAPI.write("build/decorated", trie);
        return shallowAnalyzer.getMessages();
    }
}
