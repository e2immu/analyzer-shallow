package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.junit.jupiter.api.Test;

import java.lang.constant.Constable;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaLangConstant extends CommonTest {

    @Test
    public void testConstable() {
        TypeInfo typeInfo = compiledTypesManager.get(Constable.class);
        assertSame(IMMUTABLE_HC, typeInfo.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE));
        assertSame(INDEPENDENT, typeInfo.analysis().getOrDefault(INDEPENDENT_TYPE, DEPENDENT));
        assertSame(TRUE, typeInfo.analysis().getOrDefault(CONTAINER_TYPE, FALSE));
    }

    @Test
    public void testConstableDescribeConstable() {
        TypeInfo typeInfo = compiledTypesManager.get(Constable.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("describeConstable", 0);
        assertSame(FALSE, methodInfo.analysis().getOrDefault(MODIFIED_METHOD, FALSE));
        assertSame(INDEPENDENT, methodInfo.analysis().getOrDefault(INDEPENDENT_METHOD, DEPENDENT));
        assertSame(IMMUTABLE_HC, methodInfo.analysis().getOrDefault(IMMUTABLE_METHOD, MUTABLE));
    }

}
