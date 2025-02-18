package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.*;
import org.e2immu.language.cst.api.analysis.Property;
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

import java.util.*;

import static org.e2immu.language.cst.impl.analysis.PropertyImpl.*;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.BoolImpl.FALSE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.ImmutableImpl.MUTABLE;
import static org.e2immu.language.cst.impl.analysis.ValueImpl.IndependentImpl.DEPENDENT;

public class DecoratorImpl implements Qualification.Decorator {
    private final Runtime runtime;
    private final AnalysisHelper analysisHelper;
    private final Map<Info, Info> translationMap;
    private final Set<TypeInfo> typesToImport = new HashSet<>();

    public DecoratorImpl(Runtime runtime) {
        this(runtime, null);
    }

    public DecoratorImpl(Runtime runtime, Map<Info, Info> translationMap) {
        this.runtime = runtime;
        analysisHelper = new AnalysisHelper();
        this.translationMap = translationMap;
    }

    @Override
    public List<Comment> comments(Info info) {
        Value.Message errorMessage = info.analysis().getOrDefault(PropertyImpl.ANALYZER_ERROR, ValueImpl.MessageImpl.EMPTY);
        if (!errorMessage.isEmpty()) {
            return List.of(runtime.newSingleLineComment(errorMessage.message()));
        }
        return List.of();
    }

    List<AnnotationExpression> immutableUnmodifiedIndependentContainer(PropertyValueMap analysis,
                                                                       Property independentProperty,
                                                                       Property containerProperty,
                                                                       boolean notModifiedIn,
                                                                       ParameterizedType parameterizedType) {
        if (parameterizedType.isVoidOrJavaLangVoid()) return List.of();
        boolean notTriviallyImmutable = !parameterizedType.isTriviallyImmutable();
        boolean notModified;
        boolean container;
        Value.Immutable immutable;
        Value.Independent independent;
        if (notTriviallyImmutable) {
            immutable = analysisHelper.typeImmutable(parameterizedType);
            container = analysis.getOrDefault(containerProperty, FALSE).isTrue();
            if (immutable.isMutable()) {
                notModified = notModifiedIn;
                independent = analysis.getOrDefault(independentProperty, DEPENDENT);
            } else {
                notModified = false; // no need to show
                independent = DEPENDENT; // no need to show
            }
        } else {
            notModified = false; // no need to show
            independent = DEPENDENT; // no need to show
            immutable = MUTABLE; // no need to show
            container = false;
        }
        return immutableUnmodifiedIndependentContainerAnnotations(immutable, independent, container, notModified);
    }

    private List<AnnotationExpression> immutableUnmodifiedIndependentContainerAnnotations(Value.Immutable immutable,
                                                                                          Value.Independent independent,
                                                                                          boolean container,
                                                                                          boolean notModified) {
        List<AnnotationExpression> list = new ArrayList<>();
        if (immutable.isAtLeastImmutableHC()) {
            AnnotationExpression immutableAnnotation = container
                    ? runtime.e2immuAnnotation(ImmutableContainer.class.getCanonicalName())
                    : runtime.e2immuAnnotation(Immutable.class.getCanonicalName());
            if (immutable.isImmutableHC()) {
                list.add(immutableAnnotation.withKeyValuePair("hc", runtime.constantTrue()));
            } else {
                list.add(immutableAnnotation);
            }
        } else {
            if (immutable.isFinalFields()) {
                list.add(runtime.e2immuAnnotation(FinalFields.class.getCanonicalName()));
            }
            if (container) {
                list.add(runtime.e2immuAnnotation(Container.class.getCanonicalName()));
            }
            if (independent.isAtLeastIndependentHc()) {
                AnnotationExpression independentAnnotation = runtime.e2immuAnnotation(Independent.class.getCanonicalName());
                if (independent.isIndependentHc()) {
                    list.add(independentAnnotation.withKeyValuePair("hc", runtime.constantTrue()));
                } else {
                    list.add(independentAnnotation);
                }
            }
            if (notModified) {
                list.add(runtime.e2immuAnnotation(NotModified.class.getCanonicalName()));
            }
        }
        return list;
    }

    @Override
    public List<AnnotationExpression> annotations(Info infoIn) {
        Info info = translationMap == null ? infoIn : translationMap.getOrDefault(infoIn, infoIn);

        PropertyValueMap analysis = info.analysis();
        List<AnnotationExpression> list = new ArrayList<>();

        if (info instanceof MethodInfo methodInfo) {
            boolean typeIsMutable = methodInfo.typeInfo().analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE).isMutable();
            boolean notModifying = typeIsMutable && methodInfo.isNotModifying();
            list.addAll(immutableUnmodifiedIndependentContainer(methodInfo.analysis(),
                    INDEPENDENT_METHOD, CONTAINER_METHOD, notModifying, methodInfo.returnType()));
            if (methodInfo.isPotentiallyIdentity() && !methodInfo.isNotIdentity()) {
                list.add(runtime.e2immuAnnotation(Identity.class.getCanonicalName()));
            }
        } else if (info instanceof FieldInfo fieldInfo) {
            boolean typeIsMutable = fieldInfo.typeInfo().analysis().getOrDefault(IMMUTABLE_TYPE, MUTABLE).isMutable();
            boolean unmodified = typeIsMutable && fieldInfo.isUnmodified();
            list.addAll(immutableUnmodifiedIndependentContainer(fieldInfo.analysis(), INDEPENDENT_FIELD,
                    CONTAINER_FIELD, unmodified, fieldInfo.type()));
            if (!fieldInfo.isFinal() && !fieldInfo.typeInfo().isAtLeastImmutableHC() && fieldInfo.isPropertyFinal()) {
                list.add(runtime.e2immuAnnotation(Final.class.getCanonicalName()));
            }
        } else if (info instanceof ParameterInfo pi) {
            boolean unmodified = pi.isUnmodified();
            list.addAll(immutableUnmodifiedIndependentContainer(pi.analysis(),
                    INDEPENDENT_PARAMETER, CONTAINER_PARAMETER, unmodified, pi.parameterizedType()));
        } else if (info instanceof TypeInfo) {
            Value.Immutable immutable = analysis.getOrDefault(IMMUTABLE_TYPE, MUTABLE);
            Value.Independent independent = analysis.getOrDefault(INDEPENDENT_TYPE, DEPENDENT);
            boolean container = analysis.getOrDefault(CONTAINER_TYPE, FALSE).isTrue();
            list.addAll(immutableUnmodifiedIndependentContainerAnnotations(immutable, independent, container,
                    false));
        } else throw new UnsupportedOperationException();

        list.forEach(ae -> typesToImport.add(ae.typeInfo()));
        return list;
    }

    @Override
    public List<ImportStatement> importStatements() {
        return typesToImport.stream().sorted(Comparator.comparing(TypeInfo::fullyQualifiedName))
                .map(ti -> runtime.newImportStatementBuilder().setImport(ti.fullyQualifiedName()).build())
                .toList();
    }
}
