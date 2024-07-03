package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
import org.e2immu.annotation.method.GetSet;
import org.e2immu.annotation.rare.AllowsInterrupt;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.e2immu.annotation.type.UtilityClass;
import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.util.internal.util.GetSetHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

class CommonAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonAnalyzer.class);

    protected final AnnotationProvider annotationProvider;

    CommonAnalyzer(AnnotationProvider annotationProvider) {
        this.annotationProvider = annotationProvider;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Value> T leastOfHierarchy(TypeInfo typeInfo, Property property, T defaultValue, T bestValue) {
        return typeInfo.recursiveSuperTypeStream()
                .filter(TypeInfo::isPublic)
                .map(ti -> ti.analysis().getOrDefault(property, defaultValue))
                .reduce(bestValue, (t1, t2) -> (T) t1.min(t2));
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
        Value.Bool allowInterrupt = null;
        Value.GetSetEquivalent getSetEquivalent = null;

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
                    if (methodInfo.isConstructor() || methodInfo.isFactoryMethod()) {
                        /*
                         @GetSet on a constructor or factory method has a well-defined meaning.
                         We search for the smallest constructor or factory method with the same name which
                         is compatible with a subset of the parameters. Those not in the smallest, get marked as @GetSet
                         replacements.
                        */
                        Stream<MethodInfo> candidateStream;
                        if (methodInfo.isConstructor()) {
                            candidateStream = methodInfo.typeInfo().constructors().stream();
                        } else {
                            candidateStream = methodInfo.typeInfo().methodStream()
                                    .filter(m -> m.name().equals(methodInfo.name()));
                        }
                        getSetEquivalent = findBestCompatibleMethod(candidateStream, methodInfo);
                    } else {
                        String name = ae.extractString("value", GetSetHelper.fieldName(methodInfo.name()));
                        FieldInfo field = methodInfo.typeInfo().getFieldByName(name, false);
                        if (field == null) {
                            LOGGER.warn("Cannot find field {} in {}", name, methodInfo.typeInfo());
                        } else {
                            getSetField = new ValueImpl.FieldValueImpl(field);
                        }
                    }
                }
            } else if (UtilityClass.class.getCanonicalName().equals(fqn)) {
                immutable = ValueImpl.ImmutableImpl.IMMUTABLE;
                independent = ValueImpl.IndependentImpl.INDEPENDENT;
            } else if (AllowsInterrupt.class.getCanonicalName().equals(fqn)) {
                allowInterrupt = valueForTrue;
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
            if (allowInterrupt != null) map.put(PropertyImpl.METHOD_ALLOWS_INTERRUPTS, allowInterrupt);
            if (getSetEquivalent != null) map.put(PropertyImpl.GET_SET_EQUIVALENT, getSetEquivalent);
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

    private Value.GetSetEquivalent findBestCompatibleMethod(Stream<MethodInfo> candidateStream, MethodInfo target) {
        return candidateStream
                .map(mi -> createGetSetEquivalent(mi, target))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(gse -> gse.convertToGetSet().size()))
                .orElse(null);
    }

    /*
    return null when not compatible.
     */
    private Value.GetSetEquivalent createGetSetEquivalent(MethodInfo candidate, MethodInfo target) {
        if (candidate.parameters().size() >= target.parameters().size()) return null;
        Set<ParameterInfo> params = new HashSet<>(target.parameters());
        for (ParameterInfo pi : candidate.parameters()) {
            ParameterInfo inSet = params.stream()
                    .filter(p -> p.name().equals(pi.name()) && p.parameterizedType().equals(pi.parameterizedType()))
                    .findFirst().orElse(null);
            if (inSet == null) return null;
            params.remove(inSet);
        }
        return new ValueImpl.GetSetEquivalentImpl(params, candidate);
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
