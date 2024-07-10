package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.inspection.api.parser.SourceTypes;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAnnotatedApiParser {
    @BeforeAll
    public static void beforeAll() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    @Test
    public void test() throws IOException {
        AnnotatedApiParser annotatedApiParser = new AnnotatedApiParser();
        annotatedApiParser.initialize(
                List.of(JavaInspectorImpl.JAR_WITH_PATH_PREFIX + "org/slf4j"),
                List.of("src/test/java/org/e2immu/analyzer/shallow/analyzer"),
                List.of("example."));
        List<TypeInfo> types = annotatedApiParser.typesParsed();
        assertEquals(2, types.size());
        TypeInfo t1 = types.get(0);
        assertEquals("org.e2immu.analyzer.shallow.analyzer.example.jdk.JavaLang", t1.fullyQualifiedName());
        String uri = t1.compilationUnitOrEnclosingType().getLeft().uri().toString();
        assertTrue(uri.endsWith("example/jdk/JavaLang.java"), "Have: "+uri);

        assertEquals(2, annotatedApiParser.getWarnings());

        Runtime runtime = annotatedApiParser.runtime();
        TypeInfo string = runtime.stringTypeInfo();
        TypeInfo charInfo = runtime.charTypeInfo();
        MethodInfo charConstructor = string.constructors().stream()
                .filter(c -> c.parameters().size() == 1 &&
                             c.parameters().get(0).parameterizedType().typeInfo() == charInfo)
                .findFirst().orElseThrow();
        assertEquals("java.lang.String.<init>(char[])", charConstructor.fullyQualifiedName());
        // the annotations have not been copied, they're in a map!!
        assertEquals(0, charConstructor.annotations().size());
        List<AnnotationExpression> charInfoAnnots = annotatedApiParser.annotations(charConstructor);
        assertEquals(1, charInfoAnnots.size());
        assertEquals("Independent", charInfoAnnots.get(0).typeInfo().simpleName());
    }
}
