package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.impl.analysis.PropertyProviderImpl;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.language.inspection.api.resource.InputPathEntry;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WriteDecoratedAAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteDecoratedAAPI.class);
    private final Runtime runtime;
    private final Map<String, List<InputPathEntry>> packageToInputPath;

    public WriteDecoratedAAPI(Runtime runtime, Map<String, List<InputPathEntry>> packageToInputPath) {
        this.runtime = runtime;
        this.packageToInputPath = packageToInputPath;
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
        Composer composer = new Composer(runtime, "org.e2immu", Map.of(),
                Map.of(), packageToInputPath, w -> true);
        Collection<TypeInfo> apiTypes = composer.compose(list);

        Map<Info, Info> dollarMap = composer.translateFromDollarToReal();
        composer.write(apiTypes, directory, () -> new DecoratorImpl(runtime, dollarMap));

    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
