package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.junit.jupiter.api.Test;

import java.util.function.*;
import java.util.zip.ZipOutputStream;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.TRUE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NOT_NULL;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.NotNullImpl.NULLABLE;
import static org.junit.jupiter.api.Assertions.*;

public class TestJavaUtilZip extends CommonTest {

    @Test
    public void testZipOutputStreamClose() {
        TypeInfo typeInfo = compiledTypesManager.getOrLoad(ZipOutputStream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("close", 0);
        assertTrue(methodInfo.isModifying());
    }
}
