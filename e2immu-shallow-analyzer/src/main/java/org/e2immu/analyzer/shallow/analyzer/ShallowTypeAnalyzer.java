package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ShallowTypeAnalyzer extends CommonAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowTypeAnalyzer.class);

    public ShallowTypeAnalyzer(AnnotationProvider annotationProvider) {
       super(annotationProvider);
    }

    public void analyze(TypeInfo typeInfo) {
        if (typeInfo.analysis().getOrDefault(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.FALSE).isTrue()) {
            return; // already done
        }
        List<AnnotationExpression> annotations = annotationProvider.annotations(typeInfo);
        Map<Property, Value> map = annotationsToMap(typeInfo, annotations);
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


}
