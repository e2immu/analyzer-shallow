package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.impl.analysis.PropertyProviderImpl;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.util.internal.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WriteAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteAnalysis.class);

    private final Runtime runtime;

    public WriteAnalysis(Runtime runtime) {
        this.runtime = runtime;
    }

    public void write(String destinationDirectory, Trie<TypeInfo> typeTrie) throws IOException {
        write(destinationDirectory, typeTrie, null);
    }

    public void write(String destinationDirectory, Trie<TypeInfo> typeTrie, String libraryName) throws IOException {
        File directory = new File(destinationDirectory);
        if (directory.mkdirs()) {
            LOGGER.info("Created directory {}", directory.getAbsolutePath());
        }
        Codec codec = new CodecImpl(runtime, PropertyProviderImpl::get, null, null); // we don't have to decode
        write(directory, typeTrie, codec, libraryName);
    }

    public void write(File directory, Trie<TypeInfo> typeTrie, Codec codec) throws IOException {
        write(directory, typeTrie, codec, null);
    }

    public void write(File directory, Trie<TypeInfo> typeTrie, Codec codec, String libraryNameIn) throws IOException {
        try {
            typeTrie.visitThrowing(new String[]{}, (parts, list) -> {
                String libraryName = libraryNameIn == null ? ToolChain.extractLibraryName(list) : libraryNameIn;
                write(directory, codec, parts, list, libraryName);
            });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof IOException ioe) {
                throw ioe;
            }
            throw re;
        }
    }

    private void write(File directory, Codec codec, String[] packageParts, List<TypeInfo> list, String libraryName) throws IOException {
        if (list.isEmpty()) return;
        File subDirectory = new File(directory, libraryName);
        if (subDirectory.mkdirs()) {
            LOGGER.info("Created {}", subDirectory);
        }
        String compressedPackages = Arrays.stream(packageParts).map(WriteAnalysis::capitalize)
                .collect(Collectors.joining());
        File outputFile = new File(subDirectory, compressedPackages + ".json");
        LOGGER.info("Writing {} type(s) to {}", list.size(), outputFile.getAbsolutePath());
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
            osw.write("[");
            AtomicBoolean first = new AtomicBoolean(true);
            for (TypeInfo typeInfo : list) {
                writePrimary(osw, codec, first, typeInfo);
            }
            osw.write("\n]\n");
        }
    }

    private static Codec.EncodedValue write(Codec codec, Codec.Context context, Info fieldInfo, int index) {
        Stream<Codec.EncodedPropertyValue> stream = fieldInfo.analysis().propertyValueStream()
                .map(pv -> codec.encode(context, pv.property(), pv.value()))
                .filter(Objects::nonNull); // some properties will (temporarily) not be streamed
        return codec.encode(context, fieldInfo, "" + index, stream, null);
    }

    private static Codec.EncodedValue writeMethod(Codec codec, Codec.Context context, MethodInfo methodInfo, int index) {
        List<Codec.EncodedValue> subs = new ArrayList<>(methodInfo.parameters().size());
        int p = 0;
        for (ParameterInfo parameterInfo : methodInfo.parameters()) {
            context.push(parameterInfo);
            subs.add(write(codec, context, parameterInfo, p));
            context.pop();
            p++;
        }
        Stream<Codec.EncodedPropertyValue> stream = methodInfo.analysis().propertyValueStream()
                .map(pv -> codec.encode(context, pv.property(), pv.value()))
                .filter(Objects::nonNull); // some properties will (temporarily) not be streamed
        return codec.encode(context, methodInfo, "" + index, stream, subs);
    }

    private static Codec.EncodedValue writeType(Codec codec, Codec.Context context, TypeInfo typeInfo, int index) {
        List<Codec.EncodedValue> subs = new ArrayList<>();

        int sc = 0;
        for (TypeInfo subType : typeInfo.subTypes()) {
            context.push(subType);
            Codec.EncodedValue sub = writeType(codec, context, subType, sc);
            context.pop();
            subs.add(sub);
            sc++;
        }

        int fc = 0;
        for (FieldInfo fieldInfo : typeInfo.fields()) {
            context.push(fieldInfo);
            subs.add(write(codec, context, fieldInfo, fc));
            context.pop();
            fc++;
        }
        int cc = 0;
        for (MethodInfo methodInfo : typeInfo.constructors()) {
            context.push(methodInfo);
            subs.add(writeMethod(codec, context, methodInfo, cc));
            context.pop();
            cc++;
        }
        int mc = 0;
        for (MethodInfo methodInfo : typeInfo.methods()) {
            context.push(methodInfo);
            subs.add(writeMethod(codec, context, methodInfo, mc));
            context.pop();
            mc++;
        }
        Stream<Codec.EncodedPropertyValue> stream = typeInfo.analysis().propertyValueStream()
                .map(pv -> codec.encode(context, pv.property(), pv.value()))
                .filter(Objects::nonNull); // some properties will (temporarily) not be streamed
        return codec.encode(context, typeInfo, "" + index, stream, subs);
    }

    private static void writePrimary(OutputStreamWriter osw,
                                     Codec codec,
                                     AtomicBoolean first,
                                     TypeInfo primaryType) throws IOException {
        Codec.Context context = new CodecImpl.ContextImpl();
        context.push(primaryType);
        Codec.EncodedValue ev = writeType(codec, context, primaryType, 0);
        context.pop();

        if (ev != null) {
            if (first.get()) first.set(false);
            else osw.write(",\n");
            ((CodecImpl.E) ev).write(osw, 0, true);
        } // else: no data, no need to write
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
