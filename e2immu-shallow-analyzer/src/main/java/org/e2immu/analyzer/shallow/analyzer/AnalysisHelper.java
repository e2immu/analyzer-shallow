package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;

import java.util.Map;

public class AnalysisHelper {

    public Value.Immutable typeImmutable(ParameterizedType parameterizedType) {
        return typeImmutable(parameterizedType, Map.of());
    }

    /*
     Why dynamic value? See MethodCall.dynamicImmutable().
     If the dynamic value is IMMUTABLE_HC and the computed value is MUTABLE, we still need to go through the
     immutableDeterminedByTypeParameters() code... therefore a simple remote 'max()' operation does not work.
     */
    public Value.Immutable typeImmutable(ParameterizedType parameterizedType,
                                          Map<ParameterizedType, Value.Immutable> dynamicValues) {
        if (parameterizedType.arrays() > 0) {
            return ValueImpl.ImmutableImpl.FINAL_FIELDS;
        }
        if (parameterizedType.isTypeOfNullConstant() || parameterizedType.isVoid()) {
            return ValueImpl.ImmutableImpl.NO_VALUE;
        }
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) {
            return ValueImpl.ImmutableImpl.IMMUTABLE_HC;
        }
        if (bestType.isPrimitiveExcludingVoid()) {
            return ValueImpl.ImmutableImpl.IMMUTABLE;
        }

        Value.Immutable dynamicBaseValue;
        Value.Immutable immutableOfCurrent = dynamicValues.get(parameterizedType);
        if (immutableOfCurrent != null) {
            dynamicBaseValue = immutableOfCurrent;
        } else {
            dynamicBaseValue = bestType.analysis().getOrDefault(PropertyImpl.IMMUTABLE_TYPE,
                    ValueImpl.ImmutableImpl.MUTABLE);
        }
        if (dynamicBaseValue.isAtLeastImmutableHC() && !parameterizedType.parameters().isEmpty()) {
            boolean useTypeParameters = bestType.analysis()
                    .getOrDefault(PropertyImpl.IMMUTABLE_TYPE_DETERMINED_BY_PARAMETERS, ValueImpl.BoolImpl.FALSE)
                    .isTrue();
            if (useTypeParameters) {
                return parameterizedType.parameters().stream()
                        .map(pt -> typeImmutable(pt, dynamicValues))
                        .reduce(ValueImpl.ImmutableImpl.IMMUTABLE, Value.Immutable::min);
            }
        }
        return dynamicBaseValue;
    }
}
