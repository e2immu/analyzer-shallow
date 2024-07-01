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
        map.forEach((p, v) -> {
            Value vv;
            if (p == PropertyImpl.IMMUTABLE_TYPE && isExtensible
                && ((ValueImpl.ImmutableImpl) v).isImmutable()) {
                vv = ValueImpl.ImmutableImpl.IMMUTABLE_HC;
            } else {
                vv = v;
            }
            typeInfo.analysis().set(p, vv);
        });

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
            fieldMap.forEach((p, v) -> {
                Value vv;
                if (p == PropertyImpl.FINAL_FIELD && ((ValueImpl.BoolImpl) v).isFalse()
                    && (enumField || fieldInfo.isFinal())) {
                    vv = ValueImpl.BoolImpl.TRUE;
                } else if (p == PropertyImpl.NOT_NULL_FIELD && ((ValueImpl.NotNullImpl) v).isNullable()
                           && (enumField || fieldInfo.type().isPrimitiveExcludingVoid())) {
                    vv = ValueImpl.NotNullImpl.NOT_NULL;
                } else if (p == PropertyImpl.CONTAINER_FIELD && ((ValueImpl.BoolImpl) v).isFalse()
                           && typeIsContainer(fieldInfo.type())) {
                    vv = ValueImpl.BoolImpl.TRUE;
                } else if (p == PropertyImpl.IMMUTABLE_FIELD && !((ValueImpl.ImmutableImpl) v).isImmutable()) {
                    Value.Immutable formally = analysisHelper.typeImmutable(fieldInfo.type());
                    vv = formally.max((ValueImpl.ImmutableImpl) v);
                } else if (p == PropertyImpl.INDEPENDENT_FIELD && !((ValueImpl.IndependentImpl) v).isIndependent()) {
                    Value.Independent formally = analysisHelper.typeIndependent(fieldInfo.type());
                    vv = formally.max((ValueImpl.IndependentImpl) v);
                } else {
                    vv = v;
                }
                fieldInfo.analysis().set(p, vv);
            });
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
