package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.impl.analysis.ValueImpl;

import java.util.*;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;

public class AnnotationHelper {

    private final Runtime runtime;
    private final AnalysisHelper analysisHelper;

    public AnnotationHelper(Runtime runtime) {
        this.runtime = runtime;
        this.analysisHelper = new AnalysisHelper();
    }

    private AnnotationExpression createAnnotation(Property key, Value value) {
        if (MODIFIED_METHOD.equals(key) || MODIFIED_FIELD.equals(key) || MODIFIED_PARAMETER.equals(key)) {
            if (ValueImpl.BoolImpl.TRUE.equals(value)) {
                return runtime.e2immuAnnotation(Modified.class.getCanonicalName());
            }
            return runtime.e2immuAnnotation(NotModified.class.getCanonicalName());
        }
        if (value instanceof Value.Independent independent) {
            AnnotationExpression ae = runtime.e2immuAnnotation(Independent.class.getCanonicalName());
            if (independent.isDependent()) {
                return ae.withKeyValuePair("absent", runtime.constantTrue());
            }
            if (independent.isIndependentHc()) {
                return ae.withKeyValuePair("hc", runtime.constantTrue());
            }
            return ae;
        }
        if (value instanceof Value.Immutable immutable) {
            if (immutable.isFinalFields()) {
                return runtime.e2immuAnnotation(FinalFields.class.getCanonicalName());
            }
            AnnotationExpression ae = runtime.e2immuAnnotation(Immutable.class.getCanonicalName());
            if (immutable.isMutable()) {
                return ae.withKeyValuePair("absent", runtime.constantTrue());
            }
            if (immutable.isImmutableHC()) {
                return ae.withKeyValuePair("hc", runtime.constantTrue());
            }
            return ae;
        }
        throw new UnsupportedOperationException("NYI: " + key);
    }


    public List<AnnotationExpression> annotationsToWrite(Info info, List<AnnotationExpression> overrides) {
        Map<Property, Value> overrideMap = overrides == null ? Map.of()
                : AnnotationExpressionsToPropertyValueMap.annotationsToMap(info, overrides);

        // we want to maintain the order of the annotations
        List<AnnotationExpression> map = new ArrayList<>();
        if (info instanceof ParameterInfo pi) {
            boolean mutable = analysisHelper.typeImmutable(pi.parameterizedType()).isMutable();
            if (mutable) {
                add(map, overrideMap, info, INDEPENDENT_PARAMETER, ValueImpl.IndependentImpl.DEPENDENT);
                add(map, overrideMap, info, MODIFIED_PARAMETER, FALSE);
            }
        } else if (info instanceof MethodInfo methodInfo) {
            boolean mutable = analysisHelper.typeImmutable(methodInfo.returnType()).isMutable();
            if (mutable) {
                add(map, overrideMap, info, INDEPENDENT_METHOD, ValueImpl.IndependentImpl.DEPENDENT);
            }
            add(map, overrideMap, info, MODIFIED_METHOD, FALSE);
            add(map, overrideMap, info, FLUENT_METHOD, FALSE);
            add(map, overrideMap, info, IDENTITY_METHOD, FALSE);
            add(map, overrideMap, info, METHOD_ALLOWS_INTERRUPTS, FALSE);
        } else if (info instanceof TypeInfo) {
            Value.Immutable immutable = (Value.Immutable) add(map, overrideMap, info, IMMUTABLE_TYPE,
                    ValueImpl.ImmutableImpl.MUTABLE);
            if (immutable.isMutable()) {
                add(map, overrideMap, info, INDEPENDENT_TYPE, ValueImpl.IndependentImpl.DEPENDENT);
            }
        } else if (info instanceof FieldInfo fieldInfo) {
            boolean mutable = analysisHelper.typeImmutable(fieldInfo.type()).isMutable();
            if (mutable) {
                add(map, overrideMap, info, MODIFIED_FIELD, FALSE);
            }
            if (!fieldInfo.isFinal()) {
                add(map, overrideMap, info, FINAL_FIELD, FALSE);
            }
        } else throw new UnsupportedOperationException();
        return map;
    }

    private Value add(List<AnnotationExpression> list,
                      Map<Property, Value> overrideMap,
                      Info info,
                      Property property,
                      Value defaultValue) {
        Value computed = info.analysis().getOrDefault(property, defaultValue);
        Value override = overrideMap.get(property);
        if (override != null) {
            toWrite = override;
        } else if (!computed.equals(defaultValue)) {
            list.add(createAnnotation(property, computed));
        }
        return defaultValue;
    }
}
