package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.Info;

import java.util.List;

public interface AnnotationProvider {

    List<AnnotationExpression> annotations(Info info);

}
