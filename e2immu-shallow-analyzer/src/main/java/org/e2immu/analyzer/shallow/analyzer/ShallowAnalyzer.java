package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
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
import java.util.stream.Stream;

public class ShallowAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowAnalyzer.class);

    private final AnnotationProvider annotationProvider;

    public ShallowAnalyzer(AnnotationProvider annotationProvider) {
        this.annotationProvider = annotationProvider;
    }

    public void analyze(TypeInfo typeInfo) {
        if (typeInfo.analysis().getOrDefault(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.FALSE).isTrue()) {
            return; // already done
        }
        List<AnnotationExpression> annotations = annotationProvider.annotations(typeInfo);
        Map<Property, Value> map = typeAnnotationsToMap(typeInfo, annotations);
        map.forEach(typeInfo.analysis()::set);
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

    @SuppressWarnings("unchecked")
    private <T extends Value> T leastOfHierarchy(TypeInfo typeInfo, Property property, T defaultValue, T bestValue) {
        T v;
        if (typeInfo.parentClass() != null) {
            TypeInfo parentType = typeInfo.parentClass().typeInfo();
            Value parentValue = parentType.analysis().getOrDefault(property, defaultValue);
            v = (T) leastOfHierarchy(parentType, property, defaultValue, bestValue).min(parentValue);
        } else {
            v = bestValue;
        }
        for (ParameterizedType interfaceImplemented : typeInfo.interfacesImplemented()) {
            TypeInfo interfaceType = interfaceImplemented.bestTypeInfo();
            Value interfaceValue = interfaceType.analysis().getOrDefault(property, defaultValue);
            v = (T) leastOfHierarchy(interfaceType, property, defaultValue, bestValue).min(v).min(interfaceValue);
        }
        return v;
    }

    private Map<Property, Value> typeAnnotationsToMap(TypeInfo typeInfo, List<AnnotationExpression> annotations) {
        int immutableLevel = 0;
        int independentLevel = -1;
        boolean isContainer = false;
        for (AnnotationExpression ae : annotations) {
            boolean isAbsent = ae.extractBoolean("absent");
            if (!isAbsent) {
                String fqn = ae.typeInfo().fullyQualifiedName();
                if (Immutable.class.getCanonicalName().equals(fqn)) {
                    boolean hc = ae.extractBoolean("hc");
                    immutableLevel = hc ? ValueImpl.ImmutableImpl.IMMUTABLE_HC.value()
                            : ValueImpl.ImmutableImpl.IMMUTABLE.value();
                } else if (ImmutableContainer.class.getCanonicalName().equals(fqn)) {
                    boolean hc = ae.extractBoolean("hc");
                    immutableLevel = hc ? ValueImpl.ImmutableImpl.IMMUTABLE_HC.value()
                            : ValueImpl.ImmutableImpl.IMMUTABLE.value();
                    isContainer = true;
                } else if (FinalFields.class.getCanonicalName().equals(fqn)) {
                    immutableLevel = ValueImpl.ImmutableImpl.FINAL_FIELDS.value();
                } else if (Container.class.getCanonicalName().equals(fqn)) {
                    isContainer = true;
                } else if (Independent.class.getCanonicalName().equals(fqn)) {
                    boolean hc = ae.extractBoolean("hc");
                    independentLevel = hc ? ValueImpl.IndependentImpl.INDEPENDENT_HC.value()
                            : ValueImpl.IndependentImpl.INDEPENDENT.value();
                }
            }
        }
        Value container = ValueImpl.BoolImpl.from(isContainer);
        Value.Immutable immutable = ValueImpl.ImmutableImpl.from(immutableLevel);
        Value independent;
        if (independentLevel == -1) {
            independent = simpleComputeIndependent(typeInfo, immutable);
        } else {
            independent = ValueImpl.IndependentImpl.from(independentLevel);
        }
        return Map.of(PropertyImpl.IMMUTABLE_TYPE, immutable,
                PropertyImpl.INDEPENDENT_TYPE, independent,
                PropertyImpl.CONTAINER_TYPE, container);
    }

    private Value simpleComputeIndependent(TypeInfo typeInfo, Value.Immutable immutable) {
        if (immutable.isImmutable()) return ValueImpl.IndependentImpl.INDEPENDENT;
        if (immutable.isAtLeastImmutableHC()) return ValueImpl.IndependentImpl.INDEPENDENT_HC;
        Stream<MethodInfo> stream = Stream.concat(typeInfo.methodStream(), typeInfo.constructors().stream())
                .filter(MethodInfo::isPubliclyAccessible);

        boolean allMethodsOnlyPrimitives = stream.allMatch(m ->
                (m.isConstructor() || m.isVoid() || m.returnType().isPrimitiveStringClass())
                && m.parameters().stream().allMatch(p -> p.parameterizedType().isPrimitiveStringClass()));
        if (allMethodsOnlyPrimitives) {
            return leastOfHierarchy(typeInfo, PropertyImpl.INDEPENDENT_TYPE, ValueImpl.IndependentImpl.DEPENDENT,
                    ValueImpl.IndependentImpl.INDEPENDENT);
        }
        return ValueImpl.IndependentImpl.DEPENDENT;
    }

}
