package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.analysis.PropertyValueMap;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.impl.analysis.PropertyProviderImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.parsers.json.JSONParser;
import org.parsers.json.Node;
import org.parsers.json.ast.Array;
import org.parsers.json.ast.JSONObject;
import org.parsers.json.ast.KeyValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LoadAnalyzedAnnotatedAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadAnalyzedAnnotatedAPI.class);

    public void go(JavaInspector javaInspector, AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        Codec codec = createCodec(javaInspector);
        go(codec, annotatedAPIConfiguration);
    }

    public void go(Codec codec, AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        for (String dir : annotatedAPIConfiguration.analyzedAnnotatedApiDirs()) {
            File directory = new File(dir);
            if (directory.canRead()) {
                new LoadAnalyzedAnnotatedAPI().goDir(codec, directory);
                LOGGER.info("Read json files in AAAPI {}", directory.getAbsolutePath());
            } else {
                LOGGER.warn("Path '{}' is not a directory containing analyzed annotated API files", directory);
            }
        }
    }

    public void goDir(JavaInspector javaInspector, File directory) throws IOException {
        Codec codec = createCodec(javaInspector);
        goDir(codec, directory);
    }

    public void goDir(Codec codec, File directory) throws IOException {
        File[] jsonFiles = directory.listFiles(fnf -> fnf.getName().endsWith(".json"));
        assert jsonFiles != null;
        for (File jsonFile : jsonFiles) {
            go(codec, jsonFile);
        }
    }

    public Codec createCodec(JavaInspector javaInspector) {
        Codec.PropertyProvider propertyProvider = PropertyProviderImpl::get;
        Codec.DecoderProvider decoderProvider = ValueImpl::decoder;
        Codec.TypeProvider typeProvider = fqn -> javaInspector.compiledTypesManager().getOrLoad(fqn);
        return new CodecImpl(propertyProvider, decoderProvider, typeProvider);
    }

    public void go(Codec codec, File jsonFile) throws IOException {
        LOGGER.info("Parsing {}", jsonFile);
        String s = Files.readString(jsonFile.toPath());
        JSONParser parser = new JSONParser(s);
        parser.Root();
        Node root = parser.rootNode();
        for (JSONObject jo : root.get(0).childrenOfType(JSONObject.class)) {
            processPrimaryType(codec, jo);
        }
    }

    private static void processPrimaryType(Codec codec, JSONObject jo) {
        Codec.Context context = new CodecImpl.ContextImpl();
        processSub(codec, context, jo);
    }

    private static void processSub(Codec codec, Codec.Context context, JSONObject jo) {
        KeyValuePair nameKv = (KeyValuePair) jo.get(1);
        String fullyQualifiedWithType = CodecImpl.unquote(nameKv.get(2).getSource());
        KeyValuePair dataKv = (KeyValuePair) jo.get(3);
        JSONObject dataJo = (JSONObject) dataKv.get(2);

        char type = fullyQualifiedWithType.charAt(0);
        String name = fullyQualifiedWithType.substring(1);
        Info info = codec.decodeInfo(context, type, name);
        assert info != null : "Cannot find " + name;
        context.push(info);
        processData(codec, context, info, dataJo);
        if (jo.size() > 5) {
            KeyValuePair subs = (KeyValuePair) jo.get(5);
            String subKey = subs.get(0).getSource();
            if ("\"sub\"".equals(subKey)) {
                processSub(codec, context, (JSONObject) subs.get(2));
            } else {
                assert "\"subs\"".equals(subKey);
                Array array = (Array) subs.get(2);
                for (int i = 1; i < array.size(); i += 2) {
                    processSub(codec, context, (JSONObject) array.get(i));
                }
            }
        }
        context.pop();
    }

    private static void processData(Codec codec, Codec.Context context, Info info, JSONObject dataJo) {
        List<Codec.EncodedPropertyValue> epvs = new ArrayList<>();
        for (int i = 1; i < dataJo.size(); i += 2) {
            if (dataJo.get(i) instanceof KeyValuePair kvp2) {
                String key = CodecImpl.unquote(kvp2.get(0).getSource());
                epvs.add(new Codec.EncodedPropertyValue(key, new CodecImpl.D(kvp2.get(2))));
            }
        }
        List<Codec.PropertyValue> pvs = codec.decode(context, info.analysis(), epvs.stream()).toList();
        try {
            pvs.forEach(pv -> info.analysis().set(pv.property(), pv.value()));
        } catch (IllegalStateException ise) {
            LOGGER.error("Problem while writing to {}", info);
            throw new RuntimeException(ise);
        }
    }

    private static void processLine(JavaInspector javaInspector, Codec codec, JSONObject jo) {
        KeyValuePair fqn = (KeyValuePair) jo.get(1);
        String fullyQualifiedWithType = CodecImpl.unquote(fqn.get(2).getSource());
        KeyValuePair data = (KeyValuePair) jo.get(3);
        JSONObject dataJo = (JSONObject) data.get(2);
        boolean isParameter = fullyQualifiedWithType.charAt(0) == 'P';
        char type = fullyQualifiedWithType.charAt(isParameter ? 1 : 0);

        Info info;
        if ('T' == type) {
            info = processType(javaInspector, fullyQualifiedWithType, isParameter);
        } else if ('M' == type || 'C' == type) {
            info = processMethod(javaInspector, fullyQualifiedWithType, isParameter, type);
        } else if ('F' == type) {
            info = processField(javaInspector, fullyQualifiedWithType);
        } else throw new UnsupportedOperationException();
        if (info != null) {
            List<Codec.EncodedPropertyValue> epvs = new ArrayList<>();
            for (int i = 1; i < dataJo.size(); i += 2) {
                if (dataJo.get(i) instanceof KeyValuePair kvp2) {
                    String key = CodecImpl.unquote(kvp2.get(0).getSource());
                    epvs.add(new Codec.EncodedPropertyValue(key, new CodecImpl.D(kvp2.get(2))));
                }
            }
            List<Codec.PropertyValue> pvs = null;//FIXME codec.decode(info.analysis(), epvs.stream()).toList();
            try {
                pvs.forEach(pv -> info.analysis().set(pv.property(), pv.value()));
            } catch (IllegalStateException ise) {
                LOGGER.error("Problem while writing to {}", info);
                throw new RuntimeException(ise);
            }
        }
    }

    private static Info processMethod(JavaInspector javaInspector, String fullyQualifiedWithType, boolean isParameter, char type) {
        int open = fullyQualifiedWithType.indexOf('(');
        String tmp = fullyQualifiedWithType.substring(isParameter ? 2 : 1, open);
        String typeFqn;
        if ('M' == type) {
            int dot = tmp.lastIndexOf('.');
            typeFqn = tmp.substring(0, dot);
        } else {
            typeFqn = tmp;
        }
        TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
        if (typeInfo == null) {
            // we cannot load it, so ignore!
            return null;
        }
        MethodInfo methodInfo;
        int close = fullyQualifiedWithType.lastIndexOf(')');
        int index = Integer.parseInt(fullyQualifiedWithType.substring(open + 1, close));
        if ('C' == type) {
            if (index >= typeInfo.constructors().size()) {
                LOGGER.error("Have no constructor with index {} in {}", index, typeFqn);
                methodInfo = null;
            } else {
                methodInfo = typeInfo.constructors().get(index);
            }
        } else {
            assert 'M' == type;
            if (index >= typeInfo.methods().size()) {
                LOGGER.error("Have no method with index {} in {}", index, typeFqn);
                methodInfo = null;
            } else {
                methodInfo = typeInfo.methods().get(index);
            }
        }
        Info info;
        if (isParameter && methodInfo != null) {
            int colon = fullyQualifiedWithType.lastIndexOf(':');
            int paramIndex = Integer.parseInt(fullyQualifiedWithType.substring(colon + 1));
            if (paramIndex >= methodInfo.parameters().size()) {
                LOGGER.error("Have no parameter with index {} in {}", paramIndex, methodInfo.fullyQualifiedName());
                info = null;
            } else {
                info = methodInfo.parameters().get(paramIndex);
            }
        } else {
            info = methodInfo;
        }
        if (info == null) {
            LOGGER.error("fqn value is {}, method index {}", fullyQualifiedWithType, index);
            LOGGER.error("TypeInfo {}, URI {}", typeInfo, typeInfo.compilationUnit().uri());
            int i = 0;
            for (MethodInfo mi : typeInfo.methods()) {
                LOGGER.error("m {}: {}", i, mi);
                i++;
            }
            throw new UnsupportedOperationException("Method " + fullyQualifiedWithType + " not found");
        }
        return info;
    }

    private static Info processField(JavaInspector javaInspector, String fullyQualifiedWithType) {
        int dot = fullyQualifiedWithType.lastIndexOf('.');
        String typeFqn = fullyQualifiedWithType.substring(1, dot);
        TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
        if (typeInfo == null) return null;
        int open = fullyQualifiedWithType.indexOf('(');
        String fieldName = fullyQualifiedWithType.substring(dot + 1, open);
        int index = Integer.parseInt(fullyQualifiedWithType.substring(open + 1, fullyQualifiedWithType.length() - 1));
        if (index >= typeInfo.fields().size()) {
            LOGGER.error("Have no field with index {} in {}", index, typeFqn);
        }
        FieldInfo fieldInfo = typeInfo.fields().get(index);
        assert fieldInfo.name().equals(fieldName);
        return fieldInfo;
    }

    private static Info processType(JavaInspector javaInspector, String fullyQualifiedWithType, boolean isParameter) {
        String fullyQualifiedName = fullyQualifiedWithType.substring(isParameter ? 2 : 1);
        return javaInspector.compiledTypesManager().getOrLoad(fullyQualifiedName);
    }
}
