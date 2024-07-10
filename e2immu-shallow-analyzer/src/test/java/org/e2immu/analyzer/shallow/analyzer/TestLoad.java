package org.e2immu.analyzer.shallow.analyzer;

import ch.qos.logback.classic.Level;
import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.language.inspection.api.resource.InputConfiguration;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.junit.jupiter.api.Test;
import org.parsers.json.JSONParser;
import org.parsers.json.Node;
import org.parsers.json.ast.JSONObject;
import org.parsers.json.ast.KeyValuePair;
import org.parsers.json.ast.Root;
import org.parsers.json.ast.StringLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class TestLoad {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestLoad.class);

    @Test
    public void test() throws IOException {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.e2immu.analyzer.shallow")).setLevel(Level.DEBUG);

        JavaInspectorImpl javaInspector = new JavaInspectorImpl();
        InputConfiguration inputConfiguration = new InputConfigurationImpl.Builder()
                .addClassPath(InputConfigurationImpl.DEFAULT_CLASSPATH)
                .addClassPath(JavaInspectorImpl.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(JavaInspectorImpl.JAR_WITH_PATH_PREFIX + "org/apiguardian/api")
                .addClassPath(JavaInspectorImpl.JAR_WITH_PATH_PREFIX + "org/junit/platform/commons")
                .build();
        javaInspector.initialize(inputConfiguration);

        Codec.DecoderProvider decoderProvider = ValueImpl::decoder;
        Codec codec = new CodecImpl(decoderProvider);

        File[] jsonFiles = new File("/Users/bnaudts/git/analyzer-shallow/e2immu-shallow-aapi/src/main/resources/json")
                .listFiles(fnf -> fnf.getName().endsWith(".json"));
        assert jsonFiles != null;
        for (File jsonFile : jsonFiles) {
            LOGGER.info("Parsing {}", jsonFile);
            String s = Files.readString(jsonFile.toPath());
            JSONParser parser = new JSONParser(s);
            parser.Root();
            Node root = parser.rootNode();
            assertInstanceOf(Root.class, root);
            for (JSONObject jo : root.get(0).childrenOfType(JSONObject.class)) {
                KeyValuePair fqn = (KeyValuePair) jo.get(1);
                String fullyQualifiedWithType = CodecImpl.unquote(fqn.get(2).getSource());
                KeyValuePair data = (KeyValuePair) jo.get(3);
                JSONObject dataJo = (JSONObject) data.get(2);
                char type = fullyQualifiedWithType.charAt(0);
                String fullyQualifiedName = fullyQualifiedWithType.substring(1);

                Info info;
                if ('T' == type) {
                    info = javaInspector.compiledTypesManager().getOrLoad(fullyQualifiedName);
                } else if ('M' == type) {
                    int open = fullyQualifiedWithType.indexOf('(');
                    String tmp = fullyQualifiedWithType.substring(1, open);
                    int dot = tmp.lastIndexOf('.');
                    String typeFqn = tmp.substring(0, dot);
                    TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
                    info = findMethodByFqn(typeInfo, tmp.substring(dot + 1), fullyQualifiedWithType.substring(open));
                } else if ('F' == type) {
                    int dot = fullyQualifiedWithType.lastIndexOf('.');
                    String typeFqn = fullyQualifiedWithType.substring(1, dot);
                    TypeInfo typeInfo = javaInspector.compiledTypesManager().getOrLoad(typeFqn);
                    String fieldName = fullyQualifiedWithType.substring(dot + 1);
                    info = typeInfo.getFieldByName(fieldName, true);
                } else throw new UnsupportedOperationException();

                List<Codec.EncodedPropertyValue> epvs = new ArrayList<>();
                for (int i = 1; i < dataJo.size(); i += 2) {
                    if (dataJo.get(i) instanceof KeyValuePair kvp2) {
                        String key = CodecImpl.unquote(kvp2.get(0).getSource());
                        epvs.add(new Codec.EncodedPropertyValue(key, new CodecImpl.D(kvp2.get(2))));
                    }
                }
                List<Codec.PropertyValue> pvs = codec.decode(info.analysis(), epvs.stream()).toList();
                pvs.forEach(pv -> info.analysis().set(pv.property(), pv.value()));
            }
        }
    }

    private Info findMethodByFqn(TypeInfo typeInfo, String methodName, String argString) {
        if ("<init>".equals(methodName)) {
            return typeInfo.constructors().stream().findFirst().orElseThrow();
        }
        return typeInfo.methods().stream()
                .filter(methodInfo -> methodName.equals(methodInfo.name()))
                .findFirst().orElseThrow(() -> new RuntimeException("Cannot find method " + methodName + " in type " + typeInfo));
    }
}
