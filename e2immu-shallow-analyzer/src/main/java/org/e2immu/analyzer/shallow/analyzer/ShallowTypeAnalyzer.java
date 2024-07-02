package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.Independent;
import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ShallowTypeAnalyzer extends CommonAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowTypeAnalyzer.class);
    private final AnalysisHelper analysisHelper = new AnalysisHelper();

    public ShallowTypeAnalyzer(AnnotationProvider annotationProvider) {
        super(annotationProvider);
    }

    public void analyze(TypeInfo typeInfo) {
        if (typeInfo.analysis().getOrDefault(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.FALSE).isTrue()) {
            return; // already done
        }
        typeInfo.analysis().set(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.TRUE);

        boolean isExtensible = typeInfo.isAbstract() || !typeInfo.isSealedOrFinal();
        List<AnnotationExpression> annotations = annotationProvider.annotations(typeInfo);
        Map<Property, Value> map = annotationsToMap(typeInfo, annotations);

        map.putIfAbsent(PropertyImpl.CONTAINER_TYPE, ValueImpl.BoolImpl.FALSE);
        Value.Immutable imm = (Value.Immutable) map.get(PropertyImpl.IMMUTABLE_TYPE);
        if (imm != null && imm.isImmutable() && isExtensible) {
            map.put(PropertyImpl.IMMUTABLE_TYPE, ValueImpl.ImmutableImpl.IMMUTABLE_HC);
        } else if (imm == null) {
            map.put(PropertyImpl.IMMUTABLE_TYPE, ValueImpl.ImmutableImpl.MUTABLE);
        }
        Value.Independent ind = (Value.Independent) map.get(PropertyImpl.INDEPENDENT_TYPE);
        if (ind != null && ind.isIndependent() && isExtensible) {
            map.put(PropertyImpl.INDEPENDENT_FIELD, ValueImpl.IndependentImpl.INDEPENDENT_HC);
        } else if (ind == null) {
            map.put(PropertyImpl.INDEPENDENT_FIELD, ValueImpl.IndependentImpl.DEPENDENT);
        }
        map.forEach(typeInfo.analysis()::set);

        boolean immutableDeterminedByTypeParameters = typeInfo.typeParameters().stream()
                .anyMatch(tp -> tp.annotations().stream().anyMatch(ae ->
                        Independent.class.getCanonicalName().equals(ae.typeInfo().fullyQualifiedName())));
        typeInfo.analysis().set(PropertyImpl.IMMUTABLE_TYPE_DETERMINED_BY_PARAMETERS,
                ValueImpl.BoolImpl.from(immutableDeterminedByTypeParameters));
    }

    public void analyzeFields(TypeInfo typeInfo) {
        boolean isEnum = typeInfo.typeNature().isEnum();
        for (FieldInfo fieldInfo : typeInfo.fields()) {
            if (fieldInfo.analysis().getOrDefault(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.FALSE).isTrue()) {
                continue; // already done
            }
            fieldInfo.analysis().set(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.TRUE);

            List<AnnotationExpression> fieldAnnotations = annotationProvider.annotations(fieldInfo);
            Map<Property, Value> fieldMap = annotationsToMap(fieldInfo, fieldAnnotations);
            boolean enumField = isEnum && fieldInfo.isSynthetic();

            Value.Bool ff = (Value.Bool) fieldMap.get(PropertyImpl.FINAL_FIELD);
            if (ff == null || ff.isFalse()) {
                if (enumField || fieldInfo.isFinal()) {
                    fieldMap.put(PropertyImpl.FINAL_FIELD, ValueImpl.BoolImpl.TRUE);
                } else if (ff == null) {
                    fieldMap.put(PropertyImpl.FINAL_FIELD, ValueImpl.BoolImpl.FALSE);
                }
            }
            Value.NotNull nn = (Value.NotNull) fieldMap.get(PropertyImpl.NOT_NULL_FIELD);
            if (nn == null || nn.isNullable()) {
                if (enumField || fieldInfo.type().isPrimitiveExcludingVoid()) {
                    fieldMap.put(PropertyImpl.NOT_NULL_FIELD, ValueImpl.NotNullImpl.NOT_NULL);
                } else if (nn == null) {
                    fieldMap.put(PropertyImpl.NOT_NULL_FIELD, ValueImpl.NotNullImpl.NULLABLE);
                }
            }
            Value.Bool c = (Value.Bool) fieldMap.get(PropertyImpl.CONTAINER_FIELD);
            if (c == null || c.isFalse()) {
                if (typeIsContainer(fieldInfo.type())) {
                    fieldMap.put(PropertyImpl.CONTAINER_FIELD, ValueImpl.BoolImpl.TRUE);
                } else if (c == null) {
                    fieldMap.put(PropertyImpl.CONTAINER_FIELD, ValueImpl.BoolImpl.FALSE);
                }
            }
            Value.Immutable imm = (Value.Immutable) fieldMap.get(PropertyImpl.IMMUTABLE_FIELD);
            if (imm == null || !imm.isImmutable()) {
                Value.Immutable formally = analysisHelper.typeImmutable(fieldInfo.type());
                fieldMap.put(PropertyImpl.IMMUTABLE_FIELD, formally.max(imm));
            }
            Value.Independent ind = (Value.Independent) fieldMap.get(PropertyImpl.INDEPENDENT_FIELD);
            if (ind == null || !ind.isIndependent()) {
                Value.Independent formally = analysisHelper.typeIndependent(fieldInfo.type());
                fieldMap.put(PropertyImpl.INDEPENDENT_FIELD, formally.max(ind));
            }
            fieldMap.forEach(fieldInfo.analysis()::set);
        }
    }

    private boolean typeIsContainer(ParameterizedType type) {
        TypeInfo best = type.bestTypeInfo();
        if (best == null) return true;
        return best.analysis().getOrDefault(PropertyImpl.CONTAINER_TYPE, ValueImpl.BoolImpl.FALSE).isTrue();
    }

    public void check(TypeInfo typeInfo) {
        Value.Immutable immutable = typeInfo.analysis().getOrDefault(PropertyImpl.IMMUTABLE_TYPE,
                ValueImpl.ImmutableImpl.MUTABLE);
        if (immutable.isAtLeastImmutableHC()) {
            Value.Immutable least = leastOfHierarchy(typeInfo, PropertyImpl.IMMUTABLE_TYPE, ValueImpl.ImmutableImpl.MUTABLE,
                    ValueImpl.ImmutableImpl.IMMUTABLE_HC);
            if (!least.isAtLeastImmutableHC()) {
                LOGGER.warn("@Immutable inconsistency in hierarchy");
            }
            for (FieldInfo fieldInfo : typeInfo.fields()) {
                if (fieldInfo.analysis().getOrDefault(PropertyImpl.MODIFIED_FIELD, ValueImpl.BoolImpl.FALSE).isTrue()) {
                    LOGGER.warn("Have @Modified field {} in @Immutable type {}", fieldInfo.name(), typeInfo);
                }
            }
            for (MethodInfo methodInfo : typeInfo.methods()) {
                if (methodInfo.analysis().getOrDefault(PropertyImpl.MODIFIED_METHOD, ValueImpl.BoolImpl.FALSE).isTrue()) {
                    LOGGER.warn("Have @Modified method {} in @Immutable type {}", methodInfo.name(), typeInfo);
                }
            }
        }
        Value.Bool container = typeInfo.analysis().getOrDefault(PropertyImpl.CONTAINER_TYPE, ValueImpl.BoolImpl.FALSE);
        if (container.isTrue()) {
            Value least = leastOfHierarchy(typeInfo, PropertyImpl.CONTAINER_TYPE, ValueImpl.BoolImpl.FALSE,
                    ValueImpl.BoolImpl.TRUE);
            if (least.lt(container)) {
                LOGGER.warn("@Container inconsistency in hierarchy");
            }
        }
        Value.Independent independent = typeInfo.analysis().getOrDefault(PropertyImpl.INDEPENDENT_TYPE,
                ValueImpl.IndependentImpl.DEPENDENT);
        if (independent.isAtLeastIndependentHc()) {
            Value least = leastOfHierarchy(typeInfo, PropertyImpl.INDEPENDENT_TYPE, ValueImpl.IndependentImpl.DEPENDENT,
                    ValueImpl.IndependentImpl.INDEPENDENT);
            if (least.lt(independent)) {
                LOGGER.warn("@Independent inconsistency in hierarchy");
            }
        }
        if (immutable.isImmutable() && !independent.isIndependent()
            || immutable.isAtLeastImmutableHC() && !independent.isAtLeastIndependentHc()) {
            LOGGER.warn("Inconsistency between @Independent and @Immutable");
        }
    }


}
