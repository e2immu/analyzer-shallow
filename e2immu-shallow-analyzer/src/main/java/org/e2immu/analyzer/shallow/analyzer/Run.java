package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.util.internal.graph.G;
import org.e2immu.util.internal.graph.op.Linearize;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.e2immu.language.inspection.integration.JavaInspectorImpl.JAR_WITH_PATH_PREFIX;

public class Run {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);

    public static void main(String[] args) throws IOException {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        Run run = new Run();
        run.go(new String[]{"../analyzer-shallow/e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"});
    }

    public void go(String[] args) throws IOException {
        List<TypeInfo> parsedTypes = parse(args);
        LOGGER.info("Parsed {} types", parsedTypes.size());
        WriteAnalysis wa = new WriteAnalysis();
        Trie<TypeInfo> trie = new Trie<>();
        for (TypeInfo ti : parsedTypes) {
            trie.add(ti.packageName().split("\\."), ti);
        }
        File dir = new File("build");
        File targetFile = new File(dir, "OrgE2Immu.json");
        if (targetFile.delete()) LOGGER.debug("Deleted {}", targetFile);
        wa.write(dir.getAbsolutePath(), trie);
    }

    private List<TypeInfo> parse(String[] args) throws IOException {
        LOGGER.info("I'm at {}", new File(".").getAbsolutePath());
        AnnotatedApiParser annotatedApiParser = new AnnotatedApiParser();
        annotatedApiParser.initialize(
                List.of("jmods/java.base.jmod", "jmods/java.xml.jmod", "jmods/java.net.http.jmod",
                        JAR_WITH_PATH_PREFIX + "org/e2immu/support",
                        JAR_WITH_PATH_PREFIX + "org/slf4j",
                        JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic",
                        JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api",
                        JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi",
                        JAR_WITH_PATH_PREFIX + "org/apiguardian/api"
                ),
                List.of(args[0]),
                List.of("java", "e2immu", "log", "test"));

        ShallowTypeAnalyzer shallowTypeAnalyzer = new ShallowTypeAnalyzer(annotatedApiParser);
        ShallowMethodAnalyzer shallowMethodAnalyzer = new ShallowMethodAnalyzer(annotatedApiParser);
        List<TypeInfo> types = annotatedApiParser.types();
        List<TypeInfo> allTypes = types.stream().flatMap(TypeInfo::recursiveSubTypeStream)
                .filter(TypeInfo::isPublic)
                .flatMap(t -> Stream.concat(Stream.of(t), t.recursiveSuperTypeStream()))
                .distinct()
                .toList();
        G.Builder<TypeInfo> graphBuilder = new G.Builder<>(Long::sum);
        for (TypeInfo typeInfo : allTypes) {
            List<TypeInfo> allSuperTypes = typeInfo.recursiveSuperTypeStream()
                    .filter(TypeInfo::isPublic)
                    .toList();
            graphBuilder.add(typeInfo, allSuperTypes);
        }
        G<TypeInfo> graph = graphBuilder.build();
        Linearize.Result<TypeInfo> linearize = Linearize.linearize(graph, Linearize.LinearizationMode.ALL);
        List<TypeInfo> sorted = linearize.asList(Comparator.comparing(TypeInfo::fullyQualifiedName));
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.analyze(typeInfo);
        }
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.analyzeFields(typeInfo);
            typeInfo.methodAndConstructorStream()
                    .filter(MethodInfo::isPublic)
                    .forEach(shallowMethodAnalyzer::analyze);
        }
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.check(typeInfo);
        }
        return sorted;
    }


}