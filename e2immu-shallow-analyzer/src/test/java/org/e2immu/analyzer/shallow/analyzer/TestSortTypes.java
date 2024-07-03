package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.util.internal.graph.V;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSortTypes extends CommonTest {

    @Test
    public void testSort() {
        TypeInfo autoCloseable = compiledTypesManager.get(AutoCloseable.class);
        TypeInfo zipFile = compiledTypesManager.get(ZipFile.class);

        assertTrue(allTypes.contains(autoCloseable));
        // after the superTypes(), we can see zipFile
        assertTrue(allTypes.contains(zipFile));

        V<TypeInfo> a = graph.vertex(autoCloseable);
        Map<V<TypeInfo>, Long> ea = graph.edges(a);
        assertEquals(1, ea.size());
        assertEquals("{java.lang.Object=1}", ea.toString());

        V<TypeInfo> z = graph.vertex(zipFile);
        Map<V<TypeInfo>, Long> ez = graph.edges(z);
        assertEquals(3, ez.size());
        assertEquals("java.io.Closeable=1,java.lang.AutoCloseable=1,java.lang.Object=4",
                ez.entrySet().stream().map(Objects::toString).sorted().collect(Collectors.joining(",")));

        int ia = sorted.indexOf(autoCloseable);
        int iz = sorted.indexOf(zipFile);
        assertTrue(ia < iz, ia + ">" + iz);
    }

}
