package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.analysis.Property;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
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
        List<AnnotationExpression> annotations = annotationProvider.annotations(methodInfo);
        Map<Property, Value> map = annotationsToMap(methodInfo, annotations);
        map.forEach(methodInfo.analysis()::set);
        LOGGER.debug("Finished shallow analyser on {}", methodInfo);
    }

}
