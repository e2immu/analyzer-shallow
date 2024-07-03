package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NOT_NULL;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NULLABLE;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaLang extends CommonTest {

    @Test
    public void testObject() {
        TypeInfo typeInfo = compiledTypesManager.get(Object.class);
        testImmutableContainer(typeInfo, true);
    }

    @Test
    public void testObjectToString() {
        TypeInfo typeInfo = compiledTypesManager.get(Object.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toString", 0);
        assertSame(TRUE, methodInfo.analysis().getOrDefault(CONTAINER_METHOD, FALSE));
        assertSame(NOT_NULL, methodInfo.analysis().getOrDefault(NOT_NULL_METHOD, NULLABLE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
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
    public void testStringToLowerCase() {
        TypeInfo typeInfo = compiledTypesManager.get(String.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toLowerCase", 0);
        assertFalse(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isPublic());
        assertFalse(methodInfo.isAbstract());
        assertFalse(methodInfo.isDefault());
        assertFalse(methodInfo.isStatic());
        assertEquals(10, methodInfo.complexity());
    }

    @Test
    public void testStringBuilder() {
        TypeInfo typeInfo = compiledTypesManager.get(StringBuilder.class);
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        /*
         There is no specific "length()" method in StringBuilder, we inherit from CharSequence.
         If BasicCompanionMethods_3 runs green, we are guaranteed that the CharSequence method
         is chosen over the one in AbstractStringBuilder (this type is non-public, and cannot be
         annotated / analysed).
        */
        try {
            typeInfo.findUniqueMethod("length", 0);
            fail();
        } catch (NoSuchElementException nse) {
            // ak!
        }
    }

    @Test
    public void testStringBuilderAppendBoolean() {
        TypeInfo typeInfo = compiledTypesManager.get(StringBuilder.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("append", runtime.booleanTypeInfo());
        assertSame(TRUE, methodInfo.analysis().getOrDefault(FLUENT_METHOD, FALSE));
        assertSame(TRUE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(MUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(IMMUTABLE, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testStringBuilderAppendString() {
        TypeInfo typeInfo = compiledTypesManager.get(StringBuilder.class);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("append", runtime.stringTypeInfo());
        assertSame(TRUE, methodInfo.analysis().getOrDefault(FLUENT_METHOD, FALSE));
        assertSame(TRUE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(MUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(IMMUTABLE, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(NULLABLE, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testCharSequence() {
        TypeInfo typeInfo = compiledTypesManager.get(CharSequence.class);
        testImmutableContainer(typeInfo, true);
    }

    @Test
    public void testClass() {
        TypeInfo typeInfo = compiledTypesManager.get(Class.class);
        testImmutableContainer(typeInfo, false);
    }

    //AnnotatedType[] getAnnotatedInterfaces()
    @Test
    public void testClassGetAnnotatedInterfaces() {
        TypeInfo typeInfo = compiledTypesManager.get(Class.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("getAnnotatedInterfaces", 0);
        assertSame(FALSE, methodInfo.analysis().getOrDefault(FLUENT_METHOD, FALSE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(FINAL_FIELDS, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
    }

    @Test
    public void testComparable() {
        TypeInfo typeInfo = compiledTypesManager.get(Comparable.class);
        testImmutableContainer(typeInfo, true);
    }

    @Test
    public void testComparableCompareTo() {
        TypeInfo typeInfo = compiledTypesManager.get(Comparable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("compareTo", 1);
        assertSame(FALSE, methodInfo.analysis().getOrDefault(FLUENT_METHOD, FALSE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(IDENTITY_METHOD, FALSE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        // no name in the byte-code, so auto-generated from T
        assertEquals("t", p0.name());
        assertEquals(0, p0.index());
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(IMMUTABLE_HC, p0.analysis().getOrDefault(IMMUTABLE_PARAMETER, MUTABLE));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testSerializable() {
        TypeInfo typeInfo = compiledTypesManager.get(Serializable.class);
        testImmutableContainer(typeInfo, true);
    }

    @Test
    public void testAutoCloseable() {
        TypeInfo typeInfo = compiledTypesManager.get(AutoCloseable.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }


    @Test
    public void testAppendable() {
        TypeInfo typeInfo = compiledTypesManager.get(Appendable.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testAppendableAppend() {
        TypeInfo typeInfo = compiledTypesManager.get(Appendable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("append", 3);
        assertTrue(methodInfo.overrides().isEmpty());
        assertSame(TRUE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
    }

    @Test
    public void testIterable() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterable.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testIterableForEach() {
        TypeInfo typeInfo = compiledTypesManager.get(Iterable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("forEach", 1);
        assertTrue(methodInfo.overrides().isEmpty());
        assertEquals("java.lang.Iterable.forEach(java.util.function.Consumer<? super T>)",
                methodInfo.fullyQualifiedName());
        assertTrue(methodInfo.isPublic());
        assertTrue(methodInfo.isDefault());
        assertFalse(methodInfo.isStatic());
        assertFalse(methodInfo.isAbstract());

        assertSame(FALSE, methodInfo.analysis().getOrDefault(FLUENT_METHOD, FALSE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(IDENTITY_METHOD, FALSE));
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(NO_VALUE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
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
    public void testSystemArrayCopy() {
        TypeInfo typeInfo = compiledTypesManager.get(System.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("arraycopy", 5);
        ParameterInfo p0 = methodInfo.parameters().get(0);
        // name generated from Object
        assertEquals("object", p0.name());
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));

        ParameterInfo p2 = methodInfo.parameters().get(2);
        // name generated from Object
        assertEquals("object1", p2.name());
        assertSame(INDEPENDENT, p2.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p2.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(TRUE, p2.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testBoolean() {
        TypeInfo typeInfo = compiledTypesManager.get(Boolean.class);
        testImmutableContainer(typeInfo, false);
    }

    @Test
    public void testInteger() {
        TypeInfo typeInfo = compiledTypesManager.get(Integer.class);
        testImmutableContainer(typeInfo, false);
    }

    @Test
    public void testIntegerToString() {
        TypeInfo typeInfo = compiledTypesManager.get(Integer.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toString", 0);
        assertEquals(1, methodInfo.overrides().size());
        assertEquals("java.lang.Object.toString()",
                methodInfo.overrides().stream().findFirst().orElseThrow().fullyQualifiedName());
        assertFalse(methodInfo.isStatic());
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
    }

    @Test
    public void testIntegerToStringStatic() {
        TypeInfo typeInfo = compiledTypesManager.get(Integer.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toString", 1);
        assertTrue(methodInfo.isStatic());
        assertTrue(methodInfo.overrides().isEmpty());

        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(IMMUTABLE, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        ParameterInfo p0 = methodInfo.parameters().get(0);
        // name generated from int
        assertEquals("i", p0.name());
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testNumberDoubleValue() {
        TypeInfo typeInfo = compiledTypesManager.get(Number.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("doubleValue", 0);
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
    }


    @Test
    public void testProcessHandle() {
        TypeInfo typeInfo = compiledTypesManager.get(ProcessHandle.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testReadable() {
        TypeInfo typeInfo = compiledTypesManager.get(Readable.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(FALSE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testReadableRead() {
        TypeInfo typeInfo = compiledTypesManager.get(Readable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("read", 1);
        assertSame(TRUE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(TRUE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

}
