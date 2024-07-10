package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WriteAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteAnalysis.class);

    public void write(String destinationDirectory, Trie<TypeInfo> typeTrie) {
        File directory = new File(destinationDirectory);
        Codec codec = new CodecImpl(null); // we don't have to decode
        typeTrie.visit(new String[]{}, (parts, list) -> write(directory, codec, parts, list));
    }

    private void write(File directory, Codec codec, String[] packageParts, List<TypeInfo> list) {
        if (list.isEmpty()) return;
        String compressedPackages = Arrays.stream(packageParts).map(WriteAnalysis::capitalize)
                .collect(Collectors.joining());
        File outputFile = new File(directory, compressedPackages + ".json");
        LOGGER.info("Writing {} type(s) to {}", list.size(), outputFile.getAbsolutePath());
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            osw.write("[");
            AtomicBoolean first = new AtomicBoolean(true);
            for (TypeInfo typeInfo : list) {
                write(osw, codec, first, typeInfo);
            }
            osw.write("]");
        } catch (IOException ioe) {
            LOGGER.error("Problem writing to file {}", outputFile);
            throw new RuntimeException(ioe);
        }
    }

    private void write(OutputStreamWriter osw, Codec codec, AtomicBoolean first, TypeInfo typeInfo) throws IOException {
        writeInfo(osw, codec, first, typeInfo, -1);
        for (TypeInfo subType : typeInfo.subTypes()) {
            write(osw, codec, first, subType);
        }
        int cc = 0;
        for (MethodInfo methodInfo : typeInfo.constructors()) {
            writeInfo(osw, codec, first, methodInfo, cc);
            for (ParameterInfo pi : methodInfo.parameters()) {
                writeInfo(osw, codec, first, pi, cc);
            }
            cc++;
        }
        int mc = 0;
        for (MethodInfo methodInfo : typeInfo.methods()) {
            writeInfo(osw, codec, first, methodInfo, mc);
            for (ParameterInfo pi : methodInfo.parameters()) {
                writeInfo(osw, codec, first, pi, mc);
            }
            mc++;
        }
        int fc = 0;
        for (FieldInfo fieldInfo : typeInfo.fields()) {
            writeInfo(osw, codec, first, fieldInfo, fc++);
        }
    }

    private static void writeInfo(OutputStreamWriter osw, Codec codec, AtomicBoolean first, Info info, int index) throws IOException {
        Stream<Codec.EncodedPropertyValue> stream = info.analysis().propertyValueStream()
                .map(pv -> codec.encode(pv.property(), pv.value()));
        Codec.EncodedValue ev = codec.encode(info, index, stream);
        if (first.get()) first.set(false);
        else osw.write(",\n");
        osw.write(ev.toString());
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
