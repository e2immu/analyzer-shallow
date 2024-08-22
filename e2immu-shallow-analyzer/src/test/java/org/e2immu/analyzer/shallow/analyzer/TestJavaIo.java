package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.PrintStream;
import java.io.Writer;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.FINAL_FIELDS;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NOT_NULL;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NULLABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestJavaIo extends CommonTest {
    @Test
    public void testPrintStream() {
        TypeInfo typeInfo = compiledTypesManager.get(PrintStream.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(FALSE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testPrintStreamPrintInt() {
        TypeInfo typeInfo = compiledTypesManager.get(PrintStream.class);
        TypeInfo intTypeInfo = runtime.intTypeInfo();
        MethodInfo methodInfo = typeInfo.findUniqueMethod("print", intTypeInfo);
        assertTrue(methodInfo.allowsInterrupts());

        assertTrue(methodInfo.isModifying());
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testPrintStreamPrintlnObject() {
        TypeInfo typeInfo = compiledTypesManager.get(PrintStream.class);
        TypeInfo objectTypeInfo = runtime.objectTypeInfo();
        MethodInfo methodInfo = typeInfo.findUniqueMethod("println", objectTypeInfo);
        assertTrue(methodInfo.allowsInterrupts());

        assertTrue(methodInfo.isPublic());
        assertTrue(methodInfo.isPubliclyAccessible());
        assertFalse(methodInfo.isStatic());
        assertFalse(methodInfo.isDefault());
        assertFalse(methodInfo.isAbstract());

        assertTrue(methodInfo.isModifying());
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(INDEPENDENT_HC, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NULLABLE, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(FALSE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testWriterAppendChar() {
        TypeInfo typeInfo = compiledTypesManager.get(Writer.class);
        TypeInfo charTypeInfo = runtime.charTypeInfo();
        MethodInfo methodInfo = typeInfo.findUniqueMethod("append", charTypeInfo);
        assertEquals("java.io.Writer.append(char)", methodInfo.fullyQualifiedName());
        assertTrue(methodInfo.allowsInterrupts());
        assertEquals(1, methodInfo.exceptionTypes().size());
        assertEquals("java.io.IOException", methodInfo.exceptionTypes().get(0).fullyQualifiedName());
    }

    @Test
    public void testFilterOutputStream() {
        TypeInfo typeInfo = compiledTypesManager.get(FilterOutputStream.class);
        assertSame(MUTABLE, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(DEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(FALSE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testFilterOutputStreamConstructor() {
        TypeInfo typeInfo = compiledTypesManager.get(FilterOutputStream.class);
        MethodInfo methodInfo = typeInfo.findConstructor(1);
        assertTrue(methodInfo.isConstructor());
        assertFalse(methodInfo.isCompactConstructor());
        assertFalse(methodInfo.allowsInterrupts());

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertSame(DEPENDENT, p0.analysis().getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT));
        assertSame(NOT_NULL, p0.analysis().getOrDefault(NOT_NULL_PARAMETER, NULLABLE));
        assertSame(TRUE, p0.analysis().getOrDefault(MODIFIED_PARAMETER, FALSE));
    }

    @Test
    public void testByteArrayOutputStreamToByteArray() {
        TypeInfo typeInfo = compiledTypesManager.get(ByteArrayOutputStream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toByteArray", 0);
        assertFalse(methodInfo.allowsInterrupts());

        assertFalse(methodInfo.isModifying());
        assertSame(DEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(FINAL_FIELDS, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }
}
