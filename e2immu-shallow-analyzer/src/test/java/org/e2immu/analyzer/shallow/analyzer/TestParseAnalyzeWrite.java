package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.api.parser.SourceTypes;
import org.e2immu.language.inspection.api.resource.CompiledTypesManager;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.util.internal.graph.G;
import org.e2immu.util.internal.graph.V;
import org.e2immu.util.internal.graph.op.Linearize;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;
import static org.junit.jupiter.api.Assertions.*;

public class TestParseAnalyzeWrite {
    private static CompiledTypesManager compiledTypesManager;

    static List<TypeInfo> allTypes;
    static List<TypeInfo> sorted;
    static G<TypeInfo> graph;

    @BeforeAll
    public static void beforeAll() throws IOException {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        AnnotatedApiParser annotatedApiParser = new AnnotatedApiParser();
        annotatedApiParser.initialize(
                List.of(JavaInspectorImpl.JAR_WITH_PATH_PREFIX + "org/slf4j"),
                List.of("../e2immu-shallow-aapi/src/main/java/org/e2immu/analyzer/shallow/aapi"),
                List.of("java"));

        ShallowTypeAnalyzer shallowTypeAnalyzer = new ShallowTypeAnalyzer(annotatedApiParser);
        ShallowMethodAnalyzer shallowMethodAnalyzer = new ShallowMethodAnalyzer(annotatedApiParser);
        List<TypeInfo> types = annotatedApiParser.types();
        allTypes = types.stream().flatMap(TypeInfo::recursiveSubTypeStream)
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
        graph = graphBuilder.build();
        Linearize.Result<TypeInfo> linearize = Linearize.linearize(graph, Linearize.LinearizationMode.ALL);
        sorted = linearize.asList(Comparator.comparing(TypeInfo::fullyQualifiedName));
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
        compiledTypesManager = annotatedApiParser.javaInspector().compiledTypesManager();
    }

    @Test
    public void testSort() {
        TypeInfo autoCloseable = compiledTypesManager.get(AutoCloseable.class);
        TypeInfo zipFile = compiledTypesManager.get(ZipFile.class);

        assertTrue(allTypes.contains(autoCloseable));
        // after the superTypes(), we can see zipFile
        assertTrue(allTypes.contains(zipFile));

        V<TypeInfo> a = graph.vertex(autoCloseable);
        Map<V<TypeInfo>, Long> ea = graph.edges(a);
        assertEquals(1, ea.size());
        assertEquals("{java.lang.Object=1}", ea.toString());

        V<TypeInfo> z = graph.vertex(zipFile);
        Map<V<TypeInfo>, Long> ez = graph.edges(z);
        assertEquals(3, ez.size());
        assertEquals("java.io.Closeable=1,java.lang.AutoCloseable=1,java.lang.Object=4",
                ez.entrySet().stream().map(Objects::toString).sorted().collect(Collectors.joining(",")));

        int ia = sorted.indexOf(autoCloseable);
        int iz = sorted.indexOf(zipFile);
        assertTrue(ia < iz, ia + ">" + iz);
    }

    @Test
    public void testObject() {
        TypeInfo typeInfo = compiledTypesManager.get(Object.class);
        testImmutableContainer(typeInfo, true);
    }

    @Test
    public void testString() {
        TypeInfo typeInfo = compiledTypesManager.get(String.class);
        testImmutableContainer(typeInfo, false);
    }

    @Test
    public void testStringGetBytes() {
        TypeInfo typeInfo = compiledTypesManager.get(String.class);
        MethodInfo getBytes = typeInfo.findUniqueMethod("getBytes", 4);
        assertEquals(1, getBytes.annotations().size());
        assertEquals("@Deprecated(since=\"1.1\")", getBytes.annotations().get(0).toString());
        assertSame(FALSE, getBytes.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
    }

    @Test
    public void testClass() {
        TypeInfo typeInfo = compiledTypesManager.get(Class.class);
        testImmutableContainer(typeInfo, false);
    }

    @Test
    public void testSystem() {
        TypeInfo typeInfo = compiledTypesManager.get(System.class);
        Value.Immutable immutable = typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE);
        // immutable because there are @IgnoreModifications on the exposed fields!
        assertTrue(immutable.isImmutable());
    }

    @Test
    public void testSystemOut() {
        TypeInfo typeInfo = compiledTypesManager.get(System.class);
        FieldInfo out = typeInfo.getFieldByName("out", true);
        assertTrue(out.hasBeenInspected());
        assertTrue(out.isPropertyNotNull());
        assertTrue(out.isIgnoreModifications());
        assertTrue(out.access().isPublic());
        assertTrue(out.isFinal());
        assertTrue(out.isPropertyFinal());
        assertTrue(out.isStatic());
    }

    @Test
    public void testCollection() {
        TypeInfo typeInfo = compiledTypesManager.get(Collection.class);
        assertTrue(typeInfo.isInterface());
        assertFalse(typeInfo.isAtLeastImmutableHC());
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testCollectionAdd() {
        TypeInfo typeInfo = compiledTypesManager.get(Collection.class);
        MethodInfo add = typeInfo.findUniqueMethod("add", 1);
        assertTrue(add.overrides().isEmpty());
        assertSame(TRUE, add.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
    }

    @Test
    public void testList() {
        TypeInfo typeInfo = compiledTypesManager.get(List.class);
        assertTrue(typeInfo.isInterface());
        assertFalse(typeInfo.isAtLeastImmutableHC());
        ParameterizedType collection = typeInfo.interfacesImplemented().get(0);
        assertEquals("Type java.util.Collection<E>", collection.toString());
        assertEquals("E=TP#0 in List", collection.parameters().get(0).typeParameter().toString());

        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testListAdd() {
        TypeInfo typeInfo = compiledTypesManager.get(List.class);
        MethodInfo add = typeInfo.findUniqueMethod("add", 1);
        assertEquals(1, add.overrides().size());
        MethodInfo override = add.overrides().stream().findFirst().orElseThrow();
        assertEquals("java.util.Collection.add(E)", override.fullyQualifiedName());
        assertSame(TRUE, add.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
    }

    private void testImmutableContainer(TypeInfo typeInfo, boolean hc) {
        Value.Immutable immutable = typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE,
                MUTABLE);
        Value.Immutable expectImmutable = hc ? ValueImpl.ImmutableImpl.IMMUTABLE_HC : ValueImpl.ImmutableImpl.IMMUTABLE;
        assertSame(expectImmutable, immutable);

        Value.Independent independent = typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE,
                DEPENDENT);
        assertSame(ValueImpl.IndependentImpl.INDEPENDENT, independent);
        boolean container = typeInfo.analysis().getOrDefault(CONTAINER_TYPE, ValueImpl.BoolImpl.FALSE).isTrue();
        assertTrue(container);
    }
}
