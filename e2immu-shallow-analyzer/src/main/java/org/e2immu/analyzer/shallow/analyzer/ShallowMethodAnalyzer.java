package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ShallowMethodAnalyzer extends CommonAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowMethodAnalyzer.class);

    public ShallowMethodAnalyzer(AnnotationProvider annotationProvider) {
        super(annotationProvider);
    }


    public void analyze(MethodInfo methodInfo) {
        if (methodInfo.analysis().getOrDefault(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.FALSE).isTrue()) {
            return; // already done
        }
        methodInfo.analysis().set(PropertyImpl.SHALLOW_ANALYZER, ValueImpl.BoolImpl.TRUE);

        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();
        for (ParameterInfo parameterInfo : methodInfo.parameters()) {
            List<AnnotationExpression> annotations = annotationProvider.annotations(parameterInfo);
            Map<Property, Value> map = annotationsToMap(parameterInfo, annotations);
            map.forEach((p, v) -> {
                Value vv = v;
                if (explicitlyEmpty) {
                    if (PropertyImpl.INDEPENDENT_PARAMETER == p) {
                        vv = ValueImpl.IndependentImpl.INDEPENDENT;
                    } else if (PropertyImpl.MODIFIED_PARAMETER == p && ((ValueImpl.BoolImpl) v).isTrue()) {
                        LOGGER.warn("Impossible! how can an empty method modify its argument?");
                    }
                }
                parameterInfo.analysis().set(p, vv);
            });
        }

        List<AnnotationExpression> annotations = annotationProvider.annotations(methodInfo);
        Map<Property, Value> map = annotationsToMap(methodInfo, annotations);

        map.forEach((p, v) -> {
            Value vv = v;
            if (explicitlyEmpty) {
                if (PropertyImpl.FLUENT_METHOD == p && ((ValueImpl.BoolImpl) v).isTrue()) {
                    LOGGER.warn("Impossible! how can a method without statements be @Fluent?");
                } else if (PropertyImpl.IDENTITY_METHOD == p && ((ValueImpl.BoolImpl) v).isTrue()) {
                    LOGGER.warn("Impossible! how can a method without statements be @Identity?");
                }
            } else if (PropertyImpl.FLUENT_METHOD == p && ((ValueImpl.BoolImpl) v).isFalse()) {
                vv = computeFluent(methodInfo);
            } else if (PropertyImpl.IDENTITY_METHOD == p && ((ValueImpl.BoolImpl) v).isFalse()) {
                vv = computeIdentity(methodInfo);
            } else if (PropertyImpl.MODIFIED_METHOD == p && methodInfo.isConstructor()) {
                vv = ValueImpl.BoolImpl.TRUE;
            }
            methodInfo.analysis().set(p, vv);
        });
        LOGGER.debug("Finished shallow analyser on {}", methodInfo);
    }

    private Value computeFluent(MethodInfo methodInfo) {
        return methodInfo.overrides().stream()
                .map(m -> m.analysis().getOrDefault(PropertyImpl.FLUENT_METHOD, ValueImpl.BoolImpl.FALSE))
                .reduce(ValueImpl.BoolImpl.FALSE, Value.Bool::or);
    }

    private Value computeIdentity(MethodInfo methodInfo) {
        return methodInfo.overrides().stream()
                .map(m -> m.analysis().getOrDefault(PropertyImpl.FLUENT_METHOD, ValueImpl.BoolImpl.FALSE))
                .reduce(ValueImpl.BoolImpl.FALSE, Value.Bool::or);
    }

}
