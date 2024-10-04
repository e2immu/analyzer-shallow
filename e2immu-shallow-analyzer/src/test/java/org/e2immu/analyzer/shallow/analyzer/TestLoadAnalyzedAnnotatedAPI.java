package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.PropertyImpl.INDEPENDENT_METHOD;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.IMMUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.INDEPENDENT;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NOT_NULL;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NULLABLE;
import static org.e2immu.language.inspection.integration.JavaInspectorImpl.JAR_WITH_PATH_PREFIX;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestLoadAnalyzedAnnotatedAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestLoadAnalyzedAnnotatedAPI.class);

    @Test
    public void test() throws IOException {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        List<String> classPath = List.of(
                "jmods/java.base.jmod", "jmods/java.xml.jmod", "jmods/java.net.http.jmod",
                JAR_WITH_PATH_PREFIX + "org/e2immu/support",
                JAR_WITH_PATH_PREFIX + "org/slf4j",
                JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic",
                JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api",
                JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi",
                JAR_WITH_PATH_PREFIX + "org/apiguardian/api",
                JAR_WITH_PATH_PREFIX + "org/opentest4j"
        );
        JavaInspectorImpl javaInspector = new JavaInspectorImpl();
        InputConfigurationImpl.Builder inputConfiguration = new InputConfigurationImpl.Builder();
        classPath.forEach(inputConfiguration::addClassPath);
        javaInspector.initialize(inputConfiguration.build());
        File jsonDir = new File("../e2immu-shallow-aapi/src/main/resources/json");
        assertTrue(jsonDir.isDirectory());
        new LoadAnalyzedAnnotatedAPI().goDir(javaInspector, jsonDir);

        TypeInfo typeInfo = javaInspector.compiledTypesManager().get(Object.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toString", 0);
        assertSame(TRUE, methodInfo.analysis().getOrDefault(CONTAINER_METHOD, FALSE));
        assertSame(NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
        assertFalse(methodInfo.isModifying());
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
    }
}
