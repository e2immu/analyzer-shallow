package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.e2immu.analyzer.modification.prepwork.hcs.HiddenContentSelector;
import org.e2immu.analyzer.modification.prepwork.hct.HiddenContentTypes;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import static org.e2immu.analyzer.modification.prepwork.hcs.HiddenContentSelector.HCS_PARAMETER;
import static org.e2immu.analyzer.modification.prepwork.hct.HiddenContentTypes.HIDDEN_CONTENT_TYPES;
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

public class TestLoadAnalyzedPackageFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestLoadAnalyzedPackageFiles.class);

    @BeforeAll
    public static void beforeAll() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.modification")).setLevel(Level.DEBUG);
    }

    @DisplayName("using files")
    @Test
    public void test1() throws IOException {
        List<String> classPath = List.of(
                "jmods/java.base.jmod", "jmods/java.xml.jmod", "jmods/java.net.http.jmod",
                "jmods/java.desktop.jmod", "jmods/java.datatransfer.jmod",
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

        LoadAnalyzedPackageFiles loadAnalyzedPackageFiles = new LoadAnalyzedPackageFiles();
        String jdk = ToolChain.mapJreShortNameToAnalyzedPackageShortName(ToolChain.currentJre().shortName());
        File jdkDir = new File("../e2immu-shallow-aapi/src/main/resources/org/e2immu/analyzer/shallow/aapi/analyzedPackageFiles/jdk/" + jdk);
        LOGGER.info("JDK dir is {}", jdkDir);
        assertTrue(jdkDir.isDirectory());
        int countJdk = loadAnalyzedPackageFiles.goDir(javaInspector, jdkDir);
        assertTrue(countJdk > 1);

        File libDir = new File("../e2immu-shallow-aapi/src/main/resources/org/e2immu/analyzer/shallow/aapi/analyzedPackageFiles/libs");
        LOGGER.info("Lib dir is {}", libDir);
        assertTrue(libDir.isDirectory());
        int countLib = loadAnalyzedPackageFiles.goDir(javaInspector, libDir);
        assertTrue(countLib > 0);

        doTests(javaInspector);
    }

    @DisplayName("using resource:")
    @Test
    public void test2() throws IOException {
        JavaInspectorImpl javaInspector = new JavaInspectorImpl();
        InputConfigurationImpl.Builder inputConfiguration = new InputConfigurationImpl.Builder()
                .addClassPath(InputConfigurationImpl.GRADLE_DEFAULT)
                .addClassPath(ToolChain.CLASSPATH_SLF4J_LOGBACK)
                .addClassPath(ToolChain.CLASSPATH_JUNIT)
                .addClassPath(JavaInspectorImpl.E2IMMU_SUPPORT);
        javaInspector.initialize(inputConfiguration.build());

        LoadAnalyzedPackageFiles loadAnalyzedPackageFiles = new LoadAnalyzedPackageFiles();
        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfigurationImpl.Builder()
                .addAnalyzedAnnotatedApiDirs(ToolChain.currentJdkAnalyzedPackages())
                .addAnalyzedAnnotatedApiDirs(ToolChain.commonLibsAnalyzedPackages())
                .build();
        int count = loadAnalyzedPackageFiles.go(javaInspector, annotatedAPIConfiguration);
        assertTrue(count > 1);
        doTests(javaInspector);
    }

    private static void doTests(JavaInspectorImpl javaInspector) {
        TypeInfo typeInfo = javaInspector.compiledTypesManager().get(Object.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toString", 0);
        assertSame(TRUE, methodInfo.analysis().getOrDefault(CONTAINER_METHOD, FALSE));
        assertSame(NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
        assertFalse(methodInfo.isModifying());
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        TypeInfo hashMap = javaInspector.compiledTypesManager().get(HashMap.class);
        TypeInfo sub = hashMap.findSubType("EntryIterator");
        assertEquals("EntryIterator:K, V", sub.analysis().getOrNull(HIDDEN_CONTENT_TYPES, HiddenContentTypes.class).toString());

        TypeInfo list = javaInspector.compiledTypesManager().get(List.class);
        assertEquals("0=E", list.analysis().getOrNull(HIDDEN_CONTENT_TYPES, HiddenContentTypes.class).detailedSortedTypes());
        MethodInfo listAdd = list.findUniqueMethod("add", 1);
        ParameterInfo listAdd0 = listAdd.parameters().get(0);
        assertEquals("0=*", listAdd0.analysis().getOrNull(HCS_PARAMETER, HiddenContentSelector.class).detailed());
    }
}
