package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
import org.e2immu.annotation.method.GetSet;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.e2immu.annotation.type.UtilityClass;
import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.util.internal.util.GetSetHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
        Value.Immutable immutable = null;
        Value.Independent independent = null;
        Value.NotNull notNull = null;
        Value.Bool container = null;
        Value.Bool fluent = null;
        Value.Bool identity = null;
        Value.Bool modified = null;
        Value.Bool ignoreModifications = null;
        Value.Bool isFinal = null;
        Value.FieldValue getSetField = null;

        for (AnnotationExpression ae : annotations) {
            boolean isAbsent = ae.extractBoolean("absent");
            Value.Bool valueForTrue = ValueImpl.BoolImpl.from(!isAbsent);

            String fqn = ae.typeInfo().fullyQualifiedName();
            if (Immutable.class.getCanonicalName().equals(fqn)) {
                if (isAbsent) {
                    immutable = ValueImpl.ImmutableImpl.MUTABLE;
                } else {
                    boolean hc = ae.extractBoolean("hc");
                    immutable = hc ? ValueImpl.ImmutableImpl.IMMUTABLE_HC : ValueImpl.ImmutableImpl.IMMUTABLE;
                }
            } else if (ImmutableContainer.class.getCanonicalName().equals(fqn)) {
                if (isAbsent) {
                    immutable = ValueImpl.ImmutableImpl.MUTABLE;
                    container = ValueImpl.BoolImpl.FALSE;
                } else {
                    boolean hc = ae.extractBoolean("hc");
                    immutable = hc ? ValueImpl.ImmutableImpl.IMMUTABLE_HC : ValueImpl.ImmutableImpl.IMMUTABLE;
                    container = ValueImpl.BoolImpl.TRUE;
                }
            } else if (FinalFields.class.getCanonicalName().equals(fqn)) {
                if (isAbsent) {
                    immutable = ValueImpl.ImmutableImpl.MUTABLE;
                } else {
                    immutable = ValueImpl.ImmutableImpl.FINAL_FIELDS;
                }
            } else if (Container.class.getCanonicalName().equals(fqn)) {
                container = valueForTrue;
            } else if (Independent.class.getCanonicalName().equals(fqn)) {
                if (isAbsent) {
                    independent = ValueImpl.IndependentImpl.DEPENDENT;
                } else {
                    boolean hc = ae.extractBoolean("hc");
                    independent = hc ? ValueImpl.IndependentImpl.INDEPENDENT_HC : ValueImpl.IndependentImpl.INDEPENDENT;
                }
            } else if (NotModified.class.getCanonicalName().equals(fqn)) {
                modified = ValueImpl.BoolImpl.from(isAbsent);
            } else if (Modified.class.getCanonicalName().equals(fqn)) {
                modified = valueForTrue;
            } else if (Identity.class.getCanonicalName().equals(fqn)) {
                identity = valueForTrue;
            } else if (Fluent.class.getCanonicalName().equals(fqn)) {
                fluent = valueForTrue;
            } else if (NotNull.class.getCanonicalName().equals(fqn)) {
                if (isAbsent) {
                    notNull = ValueImpl.NotNullImpl.NULLABLE;
                } else {
                    boolean content = ae.extractBoolean("content");
                    notNull = content ? ValueImpl.NotNullImpl.CONTENT_NOT_NULL : ValueImpl.NotNullImpl.NOT_NULL;
                }
            } else if (Final.class.getCanonicalName().equals(fqn)) {
                isFinal = valueForTrue;
            } else if (IgnoreModifications.class.getCanonicalName().equals(fqn)) {
                ignoreModifications = valueForTrue;
            } else if (GetSet.class.getCanonicalName().equals(fqn)) {
                if (info instanceof MethodInfo methodInfo) {
                    String name = ae.extractString("value", GetSetHelper.fieldName(methodInfo.name()));
                    FieldInfo field = methodInfo.typeInfo().getFieldByName(name, false);
                    if (field == null) {
                        LOGGER.warn("Cannot find field {} in {}", name, methodInfo.typeInfo());
                    } else {
                        getSetField = new ValueImpl.FieldValueImpl(field);
                    }
                }
            } else if (UtilityClass.class.getCanonicalName().equals(fqn)) {
                immutable = ValueImpl.ImmutableImpl.IMMUTABLE;
                independent = ValueImpl.IndependentImpl.INDEPENDENT;
            }
        }

        if (independent == null && info instanceof TypeInfo typeInfo) {
            independent = simpleComputeIndependent(typeInfo, immutable);
        }
        Map<Property, Value> map = new HashMap<>();

        if (info instanceof TypeInfo) {
            if (immutable != null) map.put(PropertyImpl.IMMUTABLE_TYPE, immutable);
            if (independent != null) map.put(PropertyImpl.INDEPENDENT_TYPE, independent);
            if (container != null) map.put(PropertyImpl.CONTAINER_TYPE, container);
            return map;
        }
        if (info instanceof MethodInfo) {
            if (fluent != null) map.put(PropertyImpl.FLUENT_METHOD, fluent);
            if (identity != null) map.put(PropertyImpl.IDENTITY_METHOD, identity);
            if (getSetField != null) map.put(PropertyImpl.GET_SET_FIELD, getSetField);
            if (immutable != null) map.put(PropertyImpl.IMMUTABLE_METHOD, immutable);
            if (independent != null) map.put(PropertyImpl.INDEPENDENT_METHOD, independent);
            if (container != null) map.put(PropertyImpl.CONTAINER_METHOD, container);
            if (notNull != null) map.put(PropertyImpl.NOT_NULL_METHOD, notNull);
            if (modified != null) map.put(PropertyImpl.MODIFIED_METHOD, modified);
            return map;
        }
        if (info instanceof FieldInfo) {
            if (immutable != null) map.put(PropertyImpl.IMMUTABLE_FIELD, immutable);
            if (independent != null) map.put(PropertyImpl.INDEPENDENT_FIELD, independent);
            if (container != null) map.put(PropertyImpl.CONTAINER_FIELD, container);
            if (notNull != null) map.put(PropertyImpl.NOT_NULL_FIELD, notNull);
            if (modified != null) map.put(PropertyImpl.MODIFIED_FIELD, modified);
            if (isFinal != null) map.put(PropertyImpl.FINAL_FIELD, isFinal);
            if (ignoreModifications != null) map.put(PropertyImpl.IGNORE_MODIFICATIONS_FIELD, ignoreModifications);
            return map;
        }
        if (info instanceof ParameterInfo) {
            if (immutable != null) map.put(PropertyImpl.IMMUTABLE_PARAMETER, immutable);
            if (independent != null) map.put(PropertyImpl.INDEPENDENT_PARAMETER, independent);
            if (container != null) map.put(PropertyImpl.CONTAINER_PARAMETER, container);
            if (notNull != null) map.put(PropertyImpl.NOT_NULL_PARAMETER, notNull);
            if (modified != null) map.put(PropertyImpl.MODIFIED_PARAMETER, modified);
            if (ignoreModifications != null) map.put(PropertyImpl.IGNORE_MODIFICATIONS_PARAMETER, ignoreModifications);
            return map;
        }
        throw new UnsupportedOperationException();
    }

    private Value.Independent simpleComputeIndependent(TypeInfo typeInfo, Value.Immutable immutable) {
        if (immutable != null) {
            if (immutable.isImmutable()) return ValueImpl.IndependentImpl.INDEPENDENT;
            if (immutable.isAtLeastImmutableHC()) return ValueImpl.IndependentImpl.INDEPENDENT_HC;
        }
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
