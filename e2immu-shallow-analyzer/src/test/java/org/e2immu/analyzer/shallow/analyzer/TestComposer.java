package org.e2immu.analyzer.shallow.analyzer;


import ch.qos.logback.classic.Level;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.TypeDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

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
                .addClassPath(JavaInspectorImpl.E2IMMU_SUPPORT)
                .addClassPath("jmod:java.base");

        JavaInspector javaInspector = new JavaInspectorImpl();
        javaInspector.initialize(inputConfigurationBuilder.build());

        Composer composer = new Composer(javaInspector, set -> "org.e2immu.testannotatedapi", w -> true);
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

        Map<Info, Info> dollarMap = composer.translateFromDollarToReal();
        composer.write(apiTypes, TEST_DIR, new DecoratorImpl(javaInspector.runtime(), dollarMap));

        String ju = Files.readString(new File(TEST_DIR, "org/e2immu/testannotatedapi/JavaUtil.java").toPath());
        assertTrue(ju.contains("//public abstract class AbstractSet extends AbstractCollection<E> implements Set<E>"));
    }

    @DisplayName("double printing of type parameters")
    @Test
    public void test2() throws IOException {
        InputConfigurationImpl.Builder inputConfigurationBuilder = new InputConfigurationImpl.Builder()
                .addSources("none")
                .addClassPath("jmod:java.base");

        JavaInspector javaInspector = new JavaInspectorImpl();
        javaInspector.initialize(inputConfigurationBuilder.build());

        Composer composer = new Composer(javaInspector, set -> "org.e2immu.testannotatedapi", w -> true);
        TypeInfo typeDescriptor = javaInspector.compiledTypesManager().getOrLoad(TypeDescriptor.class);
        Collection<TypeInfo> res = composer.compose(Set.of(typeDescriptor));
        assertEquals(1, res.size());
        @Language("java")
        String expected = """
                package org.e2immu.testannotatedapi;
                import java.lang.invoke.TypeDescriptor;
                import java.util.List;
                public class JavaLangInvoke {
                    public static final String PACKAGE_NAME = "java.lang.invoke";
                    //public interface TypeDescriptor
                    class TypeDescriptor$ {
                        //public interface OfField implements TypeDescriptor
                        class OfField<F extends TypeDescriptor.OfField<F>> {
                            boolean isArray() { return false; }
                            boolean isPrimitive() { return false; }
                            F componentType() { return null; }
                            F arrayType() { return null; }
                        }
                
                        //public interface OfMethod implements TypeDescriptor
                        class OfMethod<F extends TypeDescriptor.OfField<F>, M extends TypeDescriptor.OfMethod<F, M>> {
                            int parameterCount() { return 0; }
                            F parameterType(int i) { return null; }
                            F returnType() { return null; }
                            F [] parameterArray() { return null; }
                            List<F> parameterList() { return null; }
                            M changeReturnType(F f) { return null; }
                            M changeParameterType(int i, F f) { return null; }
                            M dropParameterTypes(int i, int i1) { return null; }
                            M insertParameterTypes(int i, F ... f) { return null; }
                        }
                        String descriptorString() { return null; }
                    }
                }
                """;
        assertEquals(expected, javaInspector.print2(res.stream().findFirst().orElseThrow()));
    }


    @DisplayName("double printing of type parameters, part 2")
    @Test
    public void test3() throws IOException {
        InputConfigurationImpl.Builder inputConfigurationBuilder = new InputConfigurationImpl.Builder()
                .addSources("none")
                .addClassPath("jmod:java.base");

        JavaInspector javaInspector = new JavaInspectorImpl();
        javaInspector.initialize(inputConfigurationBuilder.build());

        Composer composer = new Composer(javaInspector, set -> "org.e2immu.testannotatedapi", w -> true);

        TypeInfo arrays = javaInspector.compiledTypesManager().getOrLoad(Arrays.class);
        MethodInfo parallelSort = arrays.methodStream()
                .filter(mi -> "parallelSort".equals(mi.name())
                              && mi.parameters().size() == 3
                              && mi.parameters().getFirst().parameterizedType().isTypeParameter()).findFirst().orElseThrow();
        assertEquals("java.util.Arrays.parallelSort(T extends Comparable<? super T>[],int,int)",
                parallelSort.fullyQualifiedName());

        Collection<TypeInfo> res = composer.compose(Set.of(arrays));
        assertEquals(1, res.size());

        TypeInfo typeInfo = res.stream().findFirst().orElseThrow();
        String printed = javaInspector.print2(typeInfo);

        TypeInfo arraysDollar = typeInfo.findSubType("Arrays$");
        MethodInfo parallelSortCopy = arraysDollar.methodStream()
                .filter(mi -> "parallelSort".equals(mi.name())
                              && mi.parameters().size() == 3
                              && mi.parameters().getFirst().parameterizedType().isTypeParameter()).findFirst().orElseThrow();

        assertEquals("""
                org.e2immu.testannotatedapi.JavaUtil.Arrays$.parallelSort(T extends Comparable<? super T>[],int,int)\
                """, parallelSortCopy.fullyQualifiedName());

        TypeParameter tp0 = parallelSortCopy.typeParameters().getFirst();

        ParameterizedType pt0 = parallelSortCopy.parameters().getFirst().parameterizedType();
        assertSame(tp0, pt0.typeParameter());

        ParameterizedType tb0 = tp0.typeBounds().getFirst();
        assertEquals("java.lang.Comparable", tb0.typeInfo().fullyQualifiedName());
        ParameterizedType tb0p0 = tb0.parameters().getFirst();
        assertSame(tp0, tb0p0.typeParameter());

        // double printing...
        assertFalse(printed.contains("<T extends Comparable<? super T extends Comparable<? super T>>>"));
    }
}
