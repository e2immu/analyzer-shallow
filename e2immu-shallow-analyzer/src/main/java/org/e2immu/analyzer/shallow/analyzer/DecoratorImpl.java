package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.language.cst.api.analysis.PropertyValueMap;
import org.e2immu.language.cst.api.analysis.Value;
import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.ImportStatement;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.output.Qualification;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;

import java.util.ArrayList;
import java.util.List;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;

public class DecoratorImpl implements Qualification.Decorator {
    private final Runtime runtime;
    private final AnalysisHelper analysisHelper;
    private final AnnotationExpression modifiedAnnotation;
    private final TypeInfo modifiedTi;
    private final TypeInfo immutableTi;
    private final TypeInfo independentTi;

    private boolean needModifiedImport;
    private boolean needImmutableImport;
    private boolean needIndependentImport;

    public DecoratorImpl(Runtime runtime) {
        this.runtime = runtime;
        analysisHelper = new AnalysisHelper();
        modifiedTi = runtime.getFullyQualified(Modified.class, true);
        modifiedAnnotation = runtime.newAnnotationExpressionBuilder().setTypeInfo(modifiedTi).build();
        independentTi = runtime.getFullyQualified(Independent.class, true);
        immutableTi = runtime.getFullyQualified(Immutable.class, true);
    }

    @Override
    public List<Comment> comments(Info info) {
        Value.Message errorMessage = info.analysis().getOrDefault(PropertyImpl.ANALYZER_ERROR, ValueImpl.MessageImpl.EMPTY);
        if (!errorMessage.isEmpty()) {
            return List.of(runtime.newSingleLineComment(errorMessage.message()));
        }
        return List.of();
    }

    @Override
    public List<AnnotationExpression> annotations(Info info) {
        List<AnnotationExpression> list = new ArrayList<>();
        boolean modified;
        Value.Immutable immutable;
        Value.Independent independent;
        PropertyValueMap analysis = info.analysis();
        if (info instanceof MethodInfo methodInfo) {
            modified = analysis.getOrDefault(MODIFIED_METHOD, FALSE).isTrue();
            immutable = null;
            independent = nonTrivialIndependent(analysis.getOrDefault(INDEPENDENT_METHOD, DEPENDENT), methodInfo.typeInfo(),
                    methodInfo.returnType());
        } else if (info instanceof FieldInfo fieldInfo) {
            modified = analysis.getOrDefault(MODIFIED_FIELD, FALSE).isTrue();
            immutable = null;
            independent = nonTrivialIndependent(analysis.getOrDefault(INDEPENDENT_FIELD, DEPENDENT),
                    fieldInfo.owner(), fieldInfo.type());
        } else if (info instanceof ParameterInfo pi) {
            modified = analysis.getOrDefault(MODIFIED_PARAMETER, FALSE).isTrue();
            immutable = null;
            independent = nonTrivialIndependent(analysis.getOrDefault(INDEPENDENT_PARAMETER, DEPENDENT), pi.typeInfo(),
                    pi.parameterizedType());
        } else if (info instanceof TypeInfo) {
            modified = false;
            immutable = analysis.getOrDefault(IMMUTABLE_TYPE, MUTABLE);
            independent = nonTrivialIndependentType(analysis.getOrDefault(INDEPENDENT_TYPE, DEPENDENT), immutable);
        } else throw new UnsupportedOperationException();

        if (modified) {
            this.needModifiedImport = true;
            list.add(modifiedAnnotation);
        }

        if (immutable != null && !immutable.isMutable()) {
            this.needImmutableImport = true;
            AnnotationExpression.Builder b = runtime.newAnnotationExpressionBuilder().setTypeInfo(immutableTi);
            if (immutable.isImmutableHC()) {
                b.addKeyValuePair("hc", runtime.constantTrue());
            }
            list.add(b.build());
        }
        if (independent != null && !independent.isDependent()) {
            this.needIndependentImport = true;
            AnnotationExpression.Builder b = runtime.newAnnotationExpressionBuilder().setTypeInfo(independentTi);
            if (independent.isIndependentHc()) {
                b.addKeyValuePair("hc", runtime.constantTrue());
            }
            list.add(b.build());
        }
        return list;
    }

    // we're only showing INDEPENDENT when both the type and the current type are not immutable (hc or not).
    private Value.Independent nonTrivialIndependent(Value.Independent independent, TypeInfo currentType, ParameterizedType parameterizedType) {
        if (parameterizedType.isVoidOrJavaLangVoid() || parameterizedType.isPrimitiveStringClass()) return null;
        Value.Immutable immutable = analysisHelper.typeImmutable(currentType, parameterizedType);
        if (immutable.isAtLeastImmutableHC()) return null; // no need
        Value.Immutable immutableCurrent = currentType.analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE);
        if (immutableCurrent.isAtLeastImmutableHC()) return null; // no need
        return independent;
    }

    private Value.Independent nonTrivialIndependentType(Value.Independent independent, Value.Immutable immutable) {
        if (immutable.isAtLeastImmutableHC()) return null;
        return independent;
    }


    @Override
    public List<ImportStatement> importStatements() {
        List<ImportStatement> list = new ArrayList<>();
        if (needModifiedImport) {
            list.add(runtime.newImportStatement(modifiedTi.fullyQualifiedName(), false));
        }
        if (needIndependentImport) {
            list.add(runtime.newImportStatement(independentTi.fullyQualifiedName(), false));
        }
        if (needImmutableImport) {
            list.add(runtime.newImportStatement(immutableTi.fullyQualifiedName(), false));
        }
        return list;
    }
}
