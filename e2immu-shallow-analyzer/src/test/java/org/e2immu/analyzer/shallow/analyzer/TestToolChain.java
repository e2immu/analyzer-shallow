package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestToolChain {
    @BeforeAll
    public static void beforeAll() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    @Test
    public void testLinux() {
        assertEquals("openjdk-23", ToolChain.extractJdkName(
                "jar:file:/usr/lib/jvm/java-23-openjdk-arm64/jmods/java.base.jmod!/classes/java/io/BufferedInputStream.class"));
    }

    @Test
    public void test() throws IOException {
        InputConfigurationImpl.Builder inputConfigurationBuilder = new InputConfigurationImpl.Builder()
                .addSources("none")
                .addClassPath("jmods/java.base.jmod");

        JavaInspector javaInspector = new JavaInspectorImpl();
        javaInspector.initialize(inputConfigurationBuilder.build());

        String s = ToolChain.extractLibraryName(javaInspector.compiledTypesManager().typesLoaded(), false);
        assertEquals("openjdk-23.0.2", s);
    }
}
