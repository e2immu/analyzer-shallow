package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WriteDecoratedAAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteDecoratedAAPI.class);
    private final JavaInspector javaInspector;

    public WriteDecoratedAAPI(JavaInspector javaInspector) {
        this.javaInspector = javaInspector;
    }

    public void write(String destinationDirectory, Trie<TypeInfo> typeTrie) throws IOException {
        File directory = new File(destinationDirectory);
        if (directory.mkdirs()) {
            LOGGER.info("Created directory {}", directory.getAbsolutePath());
        }
        try {
            typeTrie.visitThrowing(new String[]{}, (parts, list) -> write(directory, parts, list));
        } catch (RuntimeException re) {
            if (re.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw re;
        }
    }

    private void write(File directory, String[] packageParts, List<TypeInfo> list) throws IOException {
        if (list.isEmpty()) return;
        String compressedPackages = Arrays.stream(packageParts).map(WriteDecoratedAAPI::capitalize)
                .collect(Collectors.joining());
        File outputFile = new File(directory, compressedPackages + ".json");
        LOGGER.info("Writing {} type(s) to {}", list.size(), outputFile.getAbsolutePath());
        Composer composer = new Composer(javaInspector, set -> "org.e2immu", w -> true);
        Collection<TypeInfo> apiTypes = composer.compose(list);

        Map<Info, Info> dollarMap = composer.translateFromDollarToReal();
        composer.write(apiTypes, directory, new DecoratorImpl(javaInspector.runtime(), dollarMap));

    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
