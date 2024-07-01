package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.IgnoreModifications;
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

class CommonAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonAnalyzer.class);

    protected final AnnotationProvider annotationProvider;

    CommonAnalyzer(AnnotationProvider annotationProvider) {
        this.annotationProvider = annotationProvider;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Value> T leastOfHierarchy(TypeInfo typeInfo, Property property, T defaultValue, T bestValue) {
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

    protected Map<Property, Value> annotationsToMap(Info info, List<AnnotationExpression> annotations) {
        int immutableLevel = 0;
        int independentLevel = -1;
        int notNullLevel = 0;
        boolean isContainer = false;
        boolean isFluent = false;
        boolean isIdentity = false;
        boolean isModifying = false;
        boolean isIgnoreModifications = false;
        boolean isFinal = false;
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
                } else if (NotModified.class.getCanonicalName().equals(fqn)) {
                    assert !isModifying;
                } else if (Modified.class.getCanonicalName().equals(fqn)) {
                    isModifying = true;
                } else if (Identity.class.getCanonicalName().equals(fqn)) {
                    isIdentity = true;
                } else if (Fluent.class.getCanonicalName().equals(fqn)) {
                    isFluent = true;
                } else if (NotNull.class.getCanonicalName().equals(fqn)) {
                    boolean content = ae.extractBoolean("content");
                    notNullLevel = content ? ValueImpl.NotNullImpl.CONTENT_NOT_NULL.value()
                            : ValueImpl.NotNullImpl.NOT_NULL.value();
                } else if (Final.class.getCanonicalName().equals(fqn)) {
                    isFinal = true;
                } else if (IgnoreModifications.class.getCanonicalName().equals(fqn)) {
                    isIgnoreModifications = true;
                }
            }
        }
        Value container = ValueImpl.BoolImpl.from(isContainer);
        Value.Immutable immutable = ValueImpl.ImmutableImpl.from(immutableLevel);
        Value independent;
        if (independentLevel == -1 && info instanceof TypeInfo typeInfo) {
            independent = simpleComputeIndependent(typeInfo, immutable);
        } else {
            independent = ValueImpl.IndependentImpl.from(independentLevel);
        }
        if (info instanceof TypeInfo) {
            return Map.of(PropertyImpl.IMMUTABLE_TYPE, immutable,
                    PropertyImpl.INDEPENDENT_TYPE, independent,
                    PropertyImpl.CONTAINER_TYPE, container);
        }
        Value notNull = ValueImpl.NotNullImpl.from(notNullLevel);
        if (info instanceof MethodInfo) {
            Value modified = ValueImpl.BoolImpl.from(isModifying);
            Value fluent = ValueImpl.BoolImpl.from(isFluent);
            Value identity = ValueImpl.BoolImpl.from(isIdentity);
            return Map.of(PropertyImpl.IMMUTABLE_METHOD, immutable,
                    PropertyImpl.INDEPENDENT_METHOD, independent,
                    PropertyImpl.CONTAINER_METHOD, container,
                    PropertyImpl.MODIFIED_METHOD, modified,
                    PropertyImpl.FLUENT_METHOD, fluent,
                    PropertyImpl.IDENTITY_METHOD, identity,
                    PropertyImpl.NOT_NULL_METHOD, notNull);
        }
        if (info instanceof FieldInfo) {
            Value ignoreMods = ValueImpl.BoolImpl.from(isIgnoreModifications);
            Value finalField = ValueImpl.BoolImpl.from(isFinal);
            return Map.of(PropertyImpl.NOT_NULL_FIELD, notNull,
                    PropertyImpl.FINAL_FIELD, finalField,
                    PropertyImpl.IGNORE_MODIFICATIONS_FIELD, ignoreMods);
        }
        throw new UnsupportedOperationException();
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
