package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.time.Duration;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.IMMUTABLE_METHOD;
import static org.e2immu.language.cst.impl.analysis.PropertyImpl.INDEPENDENT_METHOD;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.IMMUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.INDEPENDENT;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaLangReflect extends CommonTest {

    @Test
    public void testArrayGet() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Array.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("get", 2);
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isModifying());

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertFalse(p0.isModified());
    }

    @Test
    public void testArraySet() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(Array.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("set", 3);
        assertFalse(methodInfo.allowsInterrupts());
        assertFalse(methodInfo.isModifying());

        ParameterInfo p0 = methodInfo.parameters().get(0);
        assertTrue(p0.isModified());

        ParameterInfo p2 = methodInfo.parameters().get(2);
        assertFalse(p2.isModified());
    }

}
