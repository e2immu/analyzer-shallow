package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.language.cst.impl.runtime.RuntimeImpl;
import org.e2immu.language.inspection.api.resource.InputPathEntry;
import org.e2immu.language.inspection.resource.InputPathEntryImpl;
import org.e2immu.util.internal.util.Trie;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestWriteAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWriteAnalysis.class);

    private final Runtime runtime = new RuntimeImpl();

    @Language("json")
    private static final String EXPECT = """
            [
            {"name": "Torg.e2immu.C", "data":{"commutableMethods":["p1","p2,p3","p4"],"immutableType":3,"shallowAnalyzer":1}, "sub":
             {"name": "Mm1(0)", "data":{"shallowAnalyzer":1}}}
            ]
            """;

    @Test
    public void test() throws IOException {
        CompilationUnit cu = runtime.newCompilationUnitBuilder().setPackageName("org.e2immu").build();
        TypeInfo typeInfo = runtime.newTypeInfo(cu, "C");

        typeInfo.analysis().set(PropertyImpl.IMMUTABLE_TYPE, ValueImpl.ImmutableImpl.IMMUTABLE);
        typeInfo.analysis().set(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.TRUE);
        typeInfo.analysis().set(PropertyImpl.COMMUTABLE_METHODS,
                new ValueImpl.CommutableDataImpl("p1", "p2,p3", "p4"));

        MethodInfo methodInfo = runtime.newMethod(typeInfo, "m1", runtime.methodTypeMethod());
        methodInfo.analysis().set(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.TRUE);
        typeInfo.builder().addMethod(methodInfo);

        InputPathEntry inputPathEntry = new InputPathEntryImpl.Builder("default")
                .addPackage("org.e2immu").setHash("abc123")
                .build();
        WriteAnalysis wa = new WriteAnalysis(runtime, Map.of("org.e2immu", List.of(inputPathEntry)));
        Trie<TypeInfo> trie = new Trie<>();
        trie.add(new String[]{"org", "e2immu"}, typeInfo);
        File dir = new File("build");
        File targetFile = new File(dir, "OrgE2Immu.json");
        if (targetFile.delete()) LOGGER.debug("Deleted {}", targetFile);
        wa.write(dir.getAbsolutePath(), trie);
        String s = Files.readString(targetFile.toPath());

        assertEquals(EXPECT, s);
    }
}
