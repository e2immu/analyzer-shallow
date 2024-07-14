package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.Immutable;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.Independent;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.NO_VALUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaUtil extends CommonTest {


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
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());

        assertTrue(methodInfo.isModifying());
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
    }

    @Test
    public void testCollectionStream() {
        TypeInfo typeInfo = compiledTypesManager.get(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("stream", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());

        assertFalse(methodInfo.isModifying());
        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(MUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(CONTENT_NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
    }

    @Test
    public void testCollections() {
        TypeInfo typeInfo = compiledTypesManager.get(Collections.class);
        assertFalse(typeInfo.isInterface());
        // FIXME at the moment @UtilityClass does not enforce @Immutable
        assertSame(IMMUTABLE_HC, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(FALSE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
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


    @Test
    public void testMapPut() {
        TypeInfo typeInfo = compiledTypesManager.get(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("put", 2);
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isModifying());
        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(IMMUTABLE_HC, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));

        ParameterInfo p1 = methodInfo.parameters().get(1);
        assertFalse(p1.isModified());
        assertSame(NOT_NULL, p1.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(IMMUTABLE_HC, p1.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(INDEPENDENT_HC, p1.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
    }

    @Test
    public void testMapGetOrDefault() {
        TypeInfo typeInfo = compiledTypesManager.get(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("getOrDefault", 2);
        assertFalse(methodInfo.isStatic());
        assertFalse(methodInfo.isModifying());
        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(IMMUTABLE_HC, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));

        ParameterInfo p1 = methodInfo.parameters().get(1);
        assertFalse(p1.isModified());
        assertSame(NULLABLE, p1.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(IMMUTABLE_HC, p1.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(INDEPENDENT, p1.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
    }


    @Test
    public void testMapCopyOf() {
        TypeInfo typeInfo = compiledTypesManager.get(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("copyOf", 1);
        assertTrue(methodInfo.isStatic());
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(MUTABLE, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
    }

    @Test
    public void testMapKeySetEntrySetValues() {
        TypeInfo typeInfo = compiledTypesManager.get(Map.class);
        for (String name : new String[]{"keySet", "values", "entrySet"}) {
            MethodInfo methodInfo = typeInfo.findUniqueMethod(name, 0);
            assertTrue(methodInfo.overrides().isEmpty());
            assertFalse(methodInfo.isModifying());
            assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
            assertSame(DEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
            assertSame(MUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        }
    }

    @Test
    public void testTreeMapFirstEntry() {
        TypeInfo typeInfo = compiledTypesManager.get(TreeMap.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("firstEntry", 0);
        assertEquals(1, methodInfo.overrides().size());
        MethodInfo override = methodInfo.overrides().stream().findFirst().orElseThrow();
        assertEquals("java.util.NavigableMap.firstEntry()", override.fullyQualifiedName());
        assertFalse(methodInfo.isModifying());
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

    @Test
    public void testMapEntry() {
        TypeInfo typeInfo = compiledTypesManager.get(Map.Entry.class);
        assertTrue(typeInfo.isInterface());
        assertFalse(typeInfo.isAtLeastImmutableHC());
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT_HC, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testSortedMapValues() {
        TypeInfo typeInfo = compiledTypesManager.get(SortedMap.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("values", 0);
        assertEquals(1, methodInfo.overrides().size());
        MethodInfo override = methodInfo.overrides().stream().findFirst().orElseThrow();
        assertEquals("java.util.Map.values()", override.fullyQualifiedName());
        assertFalse(methodInfo.isModifying());
        assertSame(DEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
    }

    @Test
    public void testHashSetConstructor1() {
        TypeInfo collection = compiledTypesManager.get(Collection.class);
        TypeInfo typeInfo = compiledTypesManager.get(HashSet.class);
        MethodInfo methodInfo = typeInfo.findConstructor(collection);
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(CONTENT_NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
    }

    @Test
    public void testHashMapConstructor1() {
        TypeInfo map = compiledTypesManager.get(Map.class);
        TypeInfo typeInfo = compiledTypesManager.get(HashMap.class);
        MethodInfo methodInfo = typeInfo.findConstructor(map);
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(CONTENT_NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
    }

    @Test
    public void testObjectsRequireNonNull() {
        TypeInfo typeInfo = compiledTypesManager.get(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("requireNonNull", 1);
        assertTrue(methodInfo.isIdentity());
        assertFalse(methodInfo.isFluent());
        assertFalse(methodInfo.isModifying());
        assertTrue(methodInfo.isStatic());

        assertSame(NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
    }

    @Test
    public void testObjectsHash() {
        TypeInfo typeInfo = compiledTypesManager.get(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("hash", 1);
        assertEquals("java.util.Objects.hash(Object...)", methodInfo.fullyQualifiedName());
        assertTrue(methodInfo.isStatic());
        assertFalse(methodInfo.isIdentity());
        assertFalse(methodInfo.isFluent());
        assertFalse(methodInfo.isModifying());

        // int
        assertSame(NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertSame(NULLABLE, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(TRUE, p0.analysis().getOrDefault(CONTAINER_PARAMETER, FALSE));
    }


    @Test
    public void testIterator() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterator.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT_HC, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testIteratorNext() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("next", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isModifying());

        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

    @Test
    public void testIteratorHasNext() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("hasNext", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isModifying());

        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

    @Test
    public void testIteratorRemove() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("remove", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isModifying());

        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(NO_VALUE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

    @Test
    public void testIteratorForEachRemaining() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterator.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("forEachRemaining", 1);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isDefault());
        assertTrue(methodInfo.isModifying());

        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(NO_VALUE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
        assertTrue(p0.isIgnoreModifications());
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
    }

    @Test
    public void testRandomNextInt() {
        TypeInfo typeInfo = compiledTypesManager.get(Random.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("nextInt", 0);
        assertFalse(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertTrue(methodInfo.isModifying());

        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }


    @Test
    public void testOptional() {
        TypeInfo typeInfo = compiledTypesManager.get(Optional.class);
        assertSame(IMMUTABLE_HC, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT_HC, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testOptionalEmpty() {
        TypeInfo typeInfo = compiledTypesManager.get(Optional.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("empty", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isStatic());
        assertFalse(methodInfo.isModifying());

        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

    @Test
    public void testOptionalGet() {
        TypeInfo typeInfo = compiledTypesManager.get(Optional.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("get", 0);
        assertTrue(methodInfo.overrides().isEmpty());
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isStatic());
        assertFalse(methodInfo.isModifying());

        assertSame(INDEPENDENT_HC, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }


    @Test
    public void testTreeMap() {
        TypeInfo typeInfo = compiledTypesManager.get(TreeMap.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));

        // IMPORTANT: SequencedMap will be added as soon as we switch to Java 21; currently at 17
        assertEquals("AbstractMap,Cloneable,Map,NavigableMap,Serializable,SortedMap",
                typeInfo.superTypesExcludingJavaLangObject().stream()
                        .map(TypeInfo::simpleName).sorted().collect(Collectors.joining(",")));
    }

}
