package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.FINAL_FIELDS;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NOT_NULL;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NULLABLE;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaNioFile extends CommonTest {

    @Test
    public void testFilesWalkFileTree() {
        TypeInfo typeInfo = compiledTypesManager.get(Files.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("walkFileTree", 4);
        assertTrue(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isModifying());
        ParameterInfo p3 = methodInfo.parameters().get(3);
        assertEquals("Type java.nio.file.FileVisitor<? super java.nio.file.Path>",
                p3.parameterizedType().toString());
        assertTrue(p3.isIgnoreModifications());
    }

    @Test
    public void testFilesCreateFile() {
        TypeInfo typeInfo = compiledTypesManager.get(Files.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("createFile", 2);
        assertTrue(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isModifying());
    }

    @Test
    public void testFilesExists() {
        TypeInfo typeInfo = compiledTypesManager.get(Files.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("exists", 2);
        assertTrue(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isModifying());
    }

}
