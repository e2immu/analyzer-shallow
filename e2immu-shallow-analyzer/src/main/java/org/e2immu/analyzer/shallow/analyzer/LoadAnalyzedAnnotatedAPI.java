package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.analyzer.modification.prepwork.PrepWorkCodec;
import org.e2immu.language.cst.api.analysis.Codec;
import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.io.CodecImpl;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.api.resource.InputPathEntry;
import org.parsers.json.JSONParser;
import org.parsers.json.Node;
import org.parsers.json.ast.Array;
import org.parsers.json.ast.JSONObject;
import org.parsers.json.ast.KeyValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadAnalyzedAnnotatedAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadAnalyzedAnnotatedAPI.class);

    public void go(JavaInspector javaInspector, AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        Codec codec = new PrepWorkCodec(javaInspector.runtime()).codec();
        go(codec, annotatedAPIConfiguration);
    }

    public void go(Codec codec, AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        for (String dir : annotatedAPIConfiguration.analyzedAnnotatedApiDirs()) {
            File directory = new File(dir);
            if (directory.canRead()) {
                new LoadAnalyzedAnnotatedAPI().goDir(codec, null, directory);
                LOGGER.info("Finished reading all json files in AAAPI {}", directory.getAbsolutePath());
            } else {
                LOGGER.warn("Path '{}' is not a directory containing analyzed annotated API files", directory);
            }
        }
    }

    public Map<String, Boolean> goDir(JavaInspector javaInspector, File directory) throws IOException {
        Codec codec = new PrepWorkCodec(javaInspector.runtime()).codec();
        return goDir(codec, javaInspector.packageToInputPath(), directory);
    }

    public Map<String, Boolean> goDir(Codec codec, Map<String, List<InputPathEntry>> expectedHashPerPackage, File directory) throws IOException {
        File[] jsonFiles = directory.listFiles(fnf -> fnf.getName().endsWith(".json"));
        assert jsonFiles != null;
        Map<String, Boolean> res = new HashMap<>();
        for (File jsonFile : jsonFiles) {
            PackageLoaded pl = go(codec, expectedHashPerPackage, jsonFile);
            if (pl.packageName != null) {
                res.put(pl.packageName, pl.loaded);
            }
        }
        return res;
    }

    public record PackageLoaded(String packageName, boolean loaded) {
    }

    public PackageLoaded go(Codec codec, Map<String, List<InputPathEntry>> expectedHashPerPackage, File jsonFile) throws IOException {
        LOGGER.info("Parsing {}", jsonFile);
        String s = Files.readString(jsonFile.toPath());
        JSONParser parser = new JSONParser(s);
        parser.Root();
        Node root = parser.rootNode();
        Array main = (Array) root.get(0);
        String packageName = null;
        if (main.get(1) instanceof Array pkgPathHash) {
            packageName = CodecImpl.unquote(pkgPathHash.get(1).get(1).get(2).getSource());
            List<InputPathEntry> expectedHashes = expectedHashPerPackage.get(packageName);
            if (expectedHashes != null && differentPathEntries(pkgPathHash, expectedHashes)) {
                return new PackageLoaded(packageName, false);
            }
        }
        for (JSONObject jo : main.childrenOfType(JSONObject.class)) {
            processPrimaryType(codec, jo);
        }
        return new PackageLoaded(packageName, true); // loaded
    }

    private static boolean differentPathEntries(Array array, List<InputPathEntry> expectedHashes) {
        for (JSONObject jo : array.childrenOfType(JSONObject.class)) {
            String key = CodecImpl.unquote(jo.get(1).get(0).getSource());
            if ("path".equals(key)) {
                String path = CodecImpl.unquote(jo.get(1).get(2).getSource());
                InputPathEntry entry = expectedHashes.stream()
                        .filter(e -> path.equals(e.path())).findFirst().orElse(null);
                if (entry == null) {
                    LOGGER.debug("Computed no path/hash entry for path '{}'", path);
                    return true;
                }
                String hash = CodecImpl.unquote(jo.get(3).get(2).getSource());
                if (!hash.equals(entry.hash())) {
                    LOGGER.debug("Different hashes for path '{}': computed {}, found {} in AAAPI file", path,
                            entry.hash(), hash);
                    return true;
                }
            }
        }
        return false;
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
        Info info = codec.decodeInfoInContext(context, type, name);
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
        // the decoder writes directly into info.analysis()! we must do this, because to properly
        // decode HCS, we need the value of HCT which occurs earlier in the same list
        codec.decode(context, info.analysis(), epvs.stream());
    }
}
