package org.e2immu.analyzer.shallow.analyzer;


import ch.qos.logback.classic.Level;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.output.Formatter;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.print.FormatterImpl;
import org.e2immu.language.cst.print.FormattingOptionsImpl;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestComposer.class);
    public static final String TEST_DIR = "build/testAAPI";

    @BeforeAll
    public static void beforeAll() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);
    }

    @Test
    public void test() throws IOException {
        InputConfigurationImpl.Builder inputConfigurationBuilder = new InputConfigurationImpl.Builder()
                .addSources("none")
                .addClassPath("jmods/java.base.jmod");

        JavaInspector javaInspector = new JavaInspectorImpl();
        javaInspector.initialize(inputConfigurationBuilder.build());

        Composer composer = new Composer(javaInspector.runtime(), "org.e2immu.testannotatedapi", w -> true);
        List<TypeInfo> primaryTypes = javaInspector.compiledTypesManager()
                .typesLoaded().stream().filter(TypeInfo::isPrimaryType).toList();
        LOGGER.info("Have {} primary types loaded", primaryTypes.size());
        Collection<TypeInfo> apiTypes = composer.compose(primaryTypes);

        Path defaultDestination = Path.of(TEST_DIR);
        defaultDestination.toFile().mkdirs();
        try (Stream<Path> walk = Files.walk(defaultDestination)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .peek(System.out::println)
                    .forEach(File::delete);
        }

        composer.write(apiTypes, TEST_DIR);

        String ju = Files.readString(new File(TEST_DIR, "org/e2immu/testannotatedapi/JavaUtil.java").toPath());
        assertTrue(ju.contains("public String toString()"));
    }
}
