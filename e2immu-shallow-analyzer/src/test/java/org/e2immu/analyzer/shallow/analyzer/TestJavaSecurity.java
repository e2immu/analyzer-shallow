package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.stream.Collectors;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.IMMUTABLE_METHOD;
import static org.e2immu.language.cst.impl.analysis.PropertyImpl.INDEPENDENT_METHOD;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.IMMUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.INDEPENDENT;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaSecurity extends CommonTest {

    @Test
    public void testSecureRandomNextBytes() {
        TypeInfo typeInfo = compiledTypesManager.get(SecureRandom.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("nextBytes", 1);
        assertFalse(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isModifying());
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertTrue(p0.isModified());
    }

    @Test
    public void testMessageDigestUpdate() {
        TypeInfo typeInfo = compiledTypesManager.get(MessageDigest.class);
        assertEquals("""
                java.security.MessageDigest.update(byte), java.security.MessageDigest.update(byte[]), \
                java.security.MessageDigest.update(byte[],int,int), java.security.MessageDigest.update(java.nio.ByteBuffer)\
                """, typeInfo.methods().stream().filter(m -> "update".equals(m.name()))
                .map(Info::fullyQualifiedName)
                .sorted()
                .collect(Collectors.joining(", ")));
        MethodInfo methodInfo = typeInfo.findUniqueMethod("update", 3);
        assertFalse(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isModifying());
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
    }

    @Test
    public void testMessageDigestDigest() {
        TypeInfo typeInfo = compiledTypesManager.get(MessageDigest.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("digest", 3);
        assertFalse(methodInfo.allowsInterrupts());
        assertTrue(methodInfo.isModifying());
        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
    }

    @Test
    public void testMessageDigestGetDigestLength() {
        TypeInfo typeInfo = compiledTypesManager.get(MessageDigest.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("getDigestLength", 0);
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isModifying());
    }
}
