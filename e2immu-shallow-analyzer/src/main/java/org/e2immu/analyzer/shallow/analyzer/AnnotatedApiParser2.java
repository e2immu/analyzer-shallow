package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.StringConstant;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.inspection.api.integration.JavaInspector;
import org.e2immu.language.inspection.api.parser.Summary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/*
runs after inspection, before analysis
 */
public record AnnotatedApiParser2(JavaInspector javaInspector) {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotatedApiParser2.class);

    public record ParseResult(Map<Info, List<AnnotationExpression>> overridesAndConfirmations, List<String> warnings) {
    }

    public ParseResult parse(Collection<TypeInfo> sourceTypes, File annotatedAPILocation) throws IOException {
        Set<String> packagesToParse = sourceTypes.stream().map(TypeInfo::packageName).collect(Collectors.toUnmodifiableSet());
        Map<String, TypeInfo> typeInfoMap = sourceTypes.stream()
                .collect(Collectors.toUnmodifiableMap(Info::fullyQualifiedName, t -> t));
        File[] list = annotatedAPILocation.listFiles();
        ParseResult parseResult = new ParseResult(new HashMap<>(), new LinkedList<>());
        if (list != null) {
            for (File file : list) {
                if (file.getName().endsWith(".java")) {
                    load(file.toURI(), packagesToParse, typeInfoMap, parseResult);
                }
            }
        }
        return new ParseResult(Map.copyOf(parseResult.overridesAndConfirmations), List.copyOf(parseResult.warnings));
    }

    private void load(URI uri, Set<String> packagesToParse, Map<String, TypeInfo> typeInfoMap, ParseResult parseResult) {
        Summary summary = javaInspector.parse(uri);
        TypeInfo typeInfo = summary.firstType();
        FieldInfo packageName = typeInfo.getFieldByName("PACKAGE_NAME", false);
        if (packageName == null) {
            LOGGER.warn("Ignoring class {}, has no PACKAGE_NAME field", typeInfo);
            parseResult.warnings.add("Ignoring " + uri + ", no PACKAGE_NAME field");
            return;
        }
        String apiPackage;
        if (packageName.initializer() instanceof StringConstant sc) {
            apiPackage = sc.constant();
        } else {
            LOGGER.warn("Ignoring class {}, PACKAGE_NAME field has not been assigned a String literal", typeInfo);
            parseResult.warnings.add("Ignoring " + uri + ", PACKAGE_NAME is not assigned to a String literal");
            return;
        }
        if (packagesToParse.contains(apiPackage)) {
            LOGGER.debug("Starting AAPI inspection of {}, in API package {}", typeInfo, apiPackage);
            typeInfo.subTypes().forEach(st -> inspect(apiPackage, st, typeInfoMap, parseResult));
        } else {
            LOGGER.debug("Ignoring {}, package not relevant", uri);
        }
    }

    private void inspect(String apiPackage, TypeInfo typeInfo, Map<String, TypeInfo> typeInfoMap, ParseResult parseResult) {
        if (typeInfo.simpleName().endsWith("$")) {
            String simpleNameWithoutDollar = typeInfo.simpleName().substring(0, typeInfo.simpleName().length() - 1);
            String fqn = apiPackage + "." + simpleNameWithoutDollar;
            TypeInfo targetType = typeInfoMap.get(fqn);
            if (targetType != null) {
                transferAnnotations(typeInfo, targetType, parseResult);
            } else if (typeInfo.isPubliclyAccessible()) {
                LOGGER.warn("Ignoring type '{}', cannot load it.", fqn);
                parseResult.warnings.add("Ignoring type '" + fqn + "', not in inspected types");
            }
        } else {
            LOGGER.warn("Ignoring top level type '{}', name does not end in $.", typeInfo);
            parseResult.warnings.add("Ignoring top level type '" + typeInfo + ", name does not end in $");
        }
    }

    private boolean isConfirmedOrOverride(Info info) {
        return info.comments().stream().noneMatch(c -> c.comment().trim().startsWith("?"));
    }

    private void transferAnnotations(TypeInfo sourceType, TypeInfo targetType, ParseResult parseResult) {
        if (isConfirmedOrOverride(sourceType)) {
            List<AnnotationExpression> typeExpressions = sourceType.annotations();
            assert typeExpressions != null;
            parseResult.overridesAndConfirmations.put(targetType, typeExpressions);
        }
        for (TypeInfo subType : sourceType.subTypes()) {
            TypeInfo targetSubType = targetType.findSubType(subType.simpleName(), false);
            if (targetSubType != null) {
                transferAnnotations(subType, targetSubType, parseResult);
            } else if (subType.isPubliclyAccessible()) {
                LOGGER.warn("Ignoring subtype '{}', cannot find it in the target type '{}'",
                        subType.simpleName(), targetType);
                parseResult.warnings.add("Ignoring subtype '" + subType + "', cannot find it in the target");
            }
        }
        for (MethodInfo sourceMethod : sourceType.methods()) {
            MethodInfo targetMethod = findTargetMethod(targetType, sourceMethod);
            if (targetMethod != null) {
                if (isConfirmedOrOverride(sourceMethod)) {
                    List<AnnotationExpression> methodExpressions = sourceMethod.annotations();
                    assert methodExpressions != null;
                    parseResult.overridesAndConfirmations.put(targetMethod, methodExpressions);
                }
                doParameters(sourceMethod, targetMethod, parseResult);
            } else {
                LOGGER.warn("Ignoring method '{}', not found in target type '{}'", sourceMethod, targetType);
                parseResult.warnings.add("Ignoring method '" + sourceMethod + "', cannot find it in the target");
            }
        }
        for (MethodInfo sourceMethod : sourceType.constructors()) {
            MethodInfo targetMethod = findTargetConstructor(targetType, sourceMethod);
            if (targetMethod != null) {
                if (isConfirmedOrOverride(sourceMethod)) {
                    List<AnnotationExpression> methodExpressions = sourceMethod.annotations();
                    assert methodExpressions != null;
                    parseResult.overridesAndConfirmations.put(targetMethod, methodExpressions);
                }
                doParameters(sourceMethod, targetMethod, parseResult);
            } else {
                LOGGER.warn("Ignoring constructor '{}', not found in target type '{}'", sourceMethod, targetType);
                parseResult.warnings.add("Ignoring constructor '" + sourceMethod + "', cannot find it in the target");
            }
        }
        for (FieldInfo sourceField : sourceType.fields()) {
            FieldInfo targetField = findTargetField(targetType, sourceField);
            if (targetField != null) {
                if (isConfirmedOrOverride(sourceField)) {
                    List<AnnotationExpression> fieldExpressions = sourceField.annotations();
                    assert fieldExpressions != null;
                    parseResult.overridesAndConfirmations.put(targetField, fieldExpressions);
                }
            } else {
                LOGGER.warn("Ignoring field '{}', not found in target type '{}'", sourceField, targetType);
                parseResult.warnings.add("Ignoring field '" + sourceField + "', cannot find it in the target");
            }
        }
    }

    private void doParameters(MethodInfo sourceMethod, MethodInfo targetMethod, ParseResult parseResult) {
        int i = 0;
        for (ParameterInfo sourceParameter : sourceMethod.parameters()) {
            ParameterInfo targetParameter = targetMethod.parameters().get(i);
            if (isConfirmedOrOverride(sourceParameter)) {
                List<AnnotationExpression> paramExpressions = sourceParameter.annotations();
                assert paramExpressions != null;
                parseResult.overridesAndConfirmations.put(targetParameter, paramExpressions);
            }
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
        String sourceMethodName = trimDollar(sourceMethod.name());
        for (MethodInfo candidate : targetType.methods()) {
            if (candidate.parameters().size() == n
                && candidate.name().equals(sourceMethodName)
                && sameParameterTypes(candidate, sourceMethod)) {
                return candidate;
            }
        }
        return null; // cannot find the method, we'll NOT be looking at a supertype, since we cannot add a copy
    }

    private String trimDollar(String name) {
        if (name.endsWith("$")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
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
}
