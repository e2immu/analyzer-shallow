package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.FieldInfo;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.parsers.json.JSONParser;
import org.parsers.json.Node;
import org.parsers.json.ast.JSONObject;
import org.parsers.json.ast.KeyValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Load {
    private static final Logger LOGGER = LoggerFactory.getLogger(Load.class);

    public void go(JavaInspector javaInspector, File directory) throws IOException {
        Codec codec = createCodec(javaInspector);
        File[] jsonFiles = directory.listFiles(fnf -> fnf.getName().endsWith(".json"));
        assert jsonFiles != null;
        for (File jsonFile : jsonFiles) {
            go(javaInspector, codec, jsonFile);
        }
    }

    public Codec createCodec(JavaInspector javaInspector) {
        Codec.DecoderProvider decoderProvider = ValueImpl::decoder;
        return new CodecImpl(decoderProvider, fqn -> javaInspector.compiledTypesManager().getOrLoad(fqn));
    }

    public void go(JavaInspector javaInspector, Codec codec, File jsonFile) throws IOException {
        LOGGER.info("Parsing {}", jsonFile);
        String s = Files.readString(jsonFile.toPath());
        JSONParser parser = new JSONParser(s);
        parser.Root();
        Node root = parser.rootNode();
        for (JSONObject jo : root.get(0).childrenOfType(JSONObject.class)) {
            KeyValuePair fqn = (KeyValuePair) jo.get(1);
            String fullyQualifiedWithType = CodecImpl.unquote(fqn.get(2).getSource());
            KeyValuePair data = (KeyValuePair) jo.get(3);
            JSONObject dataJo = (JSONObject) data.get(2);
            boolean isParameter = fullyQualifiedWithType.charAt(0) == 'P';
            char type = fullyQualifiedWithType.charAt(isParameter ? 1 : 0);

            Info info;
            if ('T' == type) {
                String fullyQualifiedName = fullyQualifiedWithType.substring(isParameter ? 2 : 1);
                info = javaInspector.compiledTypesManager().getOrLoad(fullyQualifiedName);
            } else if ('M' == type || 'C' == type || isParameter) {
                int open = fullyQualifiedWithType.indexOf('(');
                String tmp = fullyQualifiedWithType.substring(isParameter ? 2 : 1, open);
                int dot = tmp.lastIndexOf('.');
                String typeFqn;
                if ('M' == type) {
                    typeFqn = tmp.substring(0, dot);
                } else {
                    typeFqn = tmp;
                }
                TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
                if (typeInfo == null) {
                    throw new UnsupportedOperationException("Cannot find type '" + typeFqn + "', extracted from '"
                                                            + fullyQualifiedWithType + "'");
                }
                MethodInfo methodInfo;
                int close = fullyQualifiedWithType.lastIndexOf(')');
                int index = Integer.parseInt(fullyQualifiedWithType.substring(open + 1, close));
                if ('C' == type) {
                    methodInfo = typeInfo.constructors().get(index);
                } else {
                    methodInfo = typeInfo.methods().get(index);
                }
                if (isParameter) {
                    int colon = fullyQualifiedWithType.lastIndexOf(':');
                    int paramIndex = Integer.parseInt(fullyQualifiedWithType.substring(colon + 1));
                    info = methodInfo.parameters().get(paramIndex);
                } else {
                    info = methodInfo;
                }
            } else if ('F' == type) {
                int dot = fullyQualifiedWithType.lastIndexOf('.');
                String typeFqn = fullyQualifiedWithType.substring(1, dot);
                TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
                int open = fullyQualifiedWithType.indexOf('(');
                String fieldName = fullyQualifiedWithType.substring(dot + 1, open);
                int index = Integer.parseInt(fullyQualifiedWithType.substring(open + 1, fullyQualifiedWithType.length() - 1));
                FieldInfo fieldInfo = typeInfo.fields().get(index);
                assert fieldInfo.name().equals(fieldName);
                info = fieldInfo;
            } else throw new UnsupportedOperationException();

            List<Codec.EncodedPropertyValue> epvs = new ArrayList<>();
            for (int i = 1; i < dataJo.size(); i += 2) {
                if (dataJo.get(i) instanceof KeyValuePair kvp2) {
                    String key = CodecImpl.unquote(kvp2.get(0).getSource());
                    epvs.add(new Codec.EncodedPropertyValue(key, new CodecImpl.D(kvp2.get(2))));
                }
            }
            List<Codec.PropertyValue> pvs = codec.decode(info.analysis(), epvs.stream()).toList();
            try {
                pvs.forEach(pv -> info.analysis().set(pv.property(), pv.value()));
            } catch (IllegalStateException ise) {
                LOGGER.error("Problem while writing to {}", info);
                throw new RuntimeException(ise);
            }
        }
    }
}
