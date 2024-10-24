package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.StringConstant;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.api.parser.Summary;
import org.e2immu.language.inspection.api.resource.InputConfiguration;
import org.e2immu.language.inspection.integration.JavaInspectorImpl;
import org.e2immu.language.inspection.resource.InputConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class AnnotatedApiParser implements AnnotationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedApiParser.class);

    private record Data(List<AnnotationExpression> annotations) {
    }

    private final List<TypeInfo> typesParsed = new ArrayList<>();
    private final Map<Info, Data> infoMap = new LinkedHashMap<>();
    private final JavaInspector javaInspector;
    private int warnings;
    private int annotatedTypes;
    private int annotations;

    public AnnotatedApiParser() {
        javaInspector = new JavaInspectorImpl();
    }

    public void initialize(InputConfiguration inputConfiguration, AnnotatedAPIConfiguration annotatedAPIConfiguration) throws IOException {
        javaInspector.initialize(inputConfiguration);
        new LoadAnalyzedAnnotatedAPI().go(javaInspector, annotatedAPIConfiguration);
        javaInspector.sourceURIs().forEach(this::load);
        javaInspector.testURIs().forEach(this::load);
        LOGGER.info("Finished parsing, annotated {} types, counted {} annotations, issued {} warning(s)",
                annotatedTypes, annotations, warnings);
    }

    public void initialize(List<String> addToClasspath, List<String> sourceDirs, List<String> packageList) throws IOException {
        InputConfigurationImpl.Builder builder = new InputConfigurationImpl.Builder()
                .addClassPath(InputConfigurationImpl.DEFAULT_CLASSPATH);
        sourceDirs.forEach(builder::addSources);
        packageList.forEach(builder::addRestrictSourceToPackages);
        addToClasspath.forEach(builder::addClassPath);
        InputConfiguration inputConfiguration = builder.build();
        initialize(inputConfiguration, new AnnotatedAPIConfigurationImpl.Builder().build());
    }

    private void load(URI uri) {
        Summary summary = javaInspector.parse(uri);
        TypeInfo typeInfo = summary.firstType();
        typesParsed.add(typeInfo);
        FieldInfo packageName = typeInfo.getFieldByName("PACKAGE_NAME", false);
        if (packageName == null) {
            LOGGER.info("Ignoring class {}, has no PACKAGE_NAME field", typeInfo);
            return;
        }
        String apiPackage;
        if (packageName.initializer() instanceof StringConstant sc) {
            apiPackage = sc.constant();
        } else {
            LOGGER.info("Ignoring class {}, PACKAGE_NAME field has not been assigned a String literal", typeInfo);
            return;
        }
        LOGGER.debug("Starting AAPI inspection of {}, in API package {}", typeInfo, apiPackage);
        typeInfo.subTypes().forEach(st -> inspect(apiPackage, st));
    }

    private void inspect(String apiPackage, TypeInfo typeInfo) {
        if (typeInfo.simpleName().endsWith("$")) {
            String simpleNameWithoutDollar = typeInfo.simpleName().substring(0, typeInfo.simpleName().length() - 1);
            String fqn = apiPackage + "." + simpleNameWithoutDollar;
            TypeInfo targetType = javaInspector.compiledTypesManager().getOrLoad(fqn);
            if (targetType != null) {
                annotatedTypes++;
                transferAnnotations(typeInfo, targetType);
            } else {
                warnings++;
                LOGGER.warn("Ignoring type '{}', cannot load it.", fqn);
            }
        } else {
            LOGGER.warn("Ignoring type '{}', name does not end in $.", typeInfo);
            warnings++;
        }
    }

    private void transferAnnotations(TypeInfo sourceType, TypeInfo targetType) {
        Data typeData = new Data(sourceType.annotations());
        annotations += sourceType.annotations().size();
        infoMap.put(targetType, typeData);

        for (TypeInfo subType : sourceType.subTypes()) {
            TypeInfo targetSubType = targetType.findSubType(subType.simpleName(), false);
            if (targetSubType != null) {
                transferAnnotations(subType, targetSubType);
            } else {
                LOGGER.warn("Ignoring subtype '{}', cannot find it in the target type '{}'",
                        subType.simpleName(), targetType);
                warnings++;
            }
        }
        for (MethodInfo sourceMethod : sourceType.methods()) {
            if (!sourceMethod.name().contains("$")) {
                MethodInfo targetMethod = findTargetMethod(targetType, sourceMethod);
                if (targetMethod != null) {
                    annotations += sourceMethod.annotations().size();
                    Data methodData = new Data(sourceMethod.annotations());
                    infoMap.put(targetMethod, methodData);
                    doParameters(sourceMethod, targetMethod);
                } else {
                    LOGGER.warn("Ignoring method '{}', not found in target type '{}'", sourceMethod, targetType);
                    ++warnings;
                }
            } // else companion method, not implemented at the moment
        }
        for (MethodInfo sourceMethod : sourceType.constructors()) {
            MethodInfo targetMethod = findTargetConstructor(targetType, sourceMethod);
            if (targetMethod != null) {
                annotations += sourceMethod.annotations().size();
                Data methodData = new Data(sourceMethod.annotations());
                infoMap.put(targetMethod, methodData);
                doParameters(sourceMethod, targetMethod);
            } else {
                LOGGER.warn("Ignoring constructor '{}', not found in target type '{}'", sourceMethod, targetType);
                ++warnings;
            }
        }
        for (FieldInfo sourceField : sourceType.fields()) {
            FieldInfo targetField = findTargetField(targetType, sourceField);
            if (targetField != null) {
                annotations += sourceField.annotations().size();
                Data fieldData = new Data(sourceField.annotations());
                infoMap.put(targetField, fieldData);
            } else {
                LOGGER.warn("Ignoring field '{}', not found in target type '{}'", sourceField, targetType);
                ++warnings;
            }
        }
    }

    private void doParameters(MethodInfo sourceMethod, MethodInfo targetMethod) {
        int i = 0;
        for (ParameterInfo sourceParameter : sourceMethod.parameters()) {
            ParameterInfo targetParameter = targetMethod.parameters().get(i);
            annotations += sourceParameter.annotations().size();
            Data paramData = new Data(sourceParameter.annotations());
            infoMap.put(targetParameter, paramData);
            i++;
        }
    }

    private MethodInfo findTargetConstructor(TypeInfo targetType, MethodInfo sourceMethod) {
        int n = sourceMethod.parameters().size();
        for (MethodInfo candidate : targetType.constructors()) {
            if (candidate.parameters().size() == n && sameParameterTypes(candidate, sourceMethod)) {
                return candidate;
            }
        }
        return null;
    }

    private MethodInfo findTargetMethod(TypeInfo targetType, MethodInfo sourceMethod) {
        int n = sourceMethod.parameters().size();
        for (MethodInfo candidate : targetType.methods()) {
            if (candidate.parameters().size() == n
                && candidate.name().equals(sourceMethod.name())
                && sameParameterTypes(candidate, sourceMethod)) {
                return candidate;
            }
        }
        return null; // cannot find the method, we'll NOT be looking at a supertype, since we cannot add a copy
    }

    private FieldInfo findTargetField(TypeInfo targetType, FieldInfo sourceField) {
        return targetType.getFieldByName(sourceField.name(), false);
    }

    private boolean sameParameterTypes(MethodInfo candidate, MethodInfo sourceMethod) {
        Iterator<ParameterInfo> it = candidate.parameters().iterator();
        for (ParameterInfo pi : sourceMethod.parameters()) {
            assert it.hasNext();
            ParameterInfo pi2 = it.next();
            if (!sameType(pi.parameterizedType(), pi2.parameterizedType())) return false;
        }
        return true;
    }

    private boolean sameType(ParameterizedType pt1, ParameterizedType pt2) {
        if (pt1.typeInfo() != null) return pt1.arrays() == pt2.arrays() && pt1.typeInfo() == pt2.typeInfo();
        return (pt1.typeParameter() == null) == (pt2.typeParameter() == null);
    }

    // for testing
    public Runtime runtime() {
        return javaInspector.runtime();
    }

    @Override
    public List<AnnotationExpression> annotations(Info info) {
        Data data = infoMap.get(info);
        if (data != null) return data.annotations;
        return List.of();
    }

    public int getWarnings() {
        return warnings;
    }

    public List<TypeInfo> types() {
        return infoMap.keySet().stream().filter(i -> i instanceof TypeInfo).map(t -> (TypeInfo) t).toList();
    }

    public Set<Info> infos() {
        return infoMap.keySet();
    }

    public JavaInspector javaInspector() {
        return javaInspector;
    }

    public List<TypeInfo> typesParsed() {
        return typesParsed;
    }
}
