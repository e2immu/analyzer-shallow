package org.e2immu.analyzer.shallow.analyzer;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.info.MethodInfo;
import org.e2immu.language.cst.api.info.ParameterInfo;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.analysis.Message;
import org.e2immu.language.cst.impl.analysis.MessageImpl;
import org.e2immu.language.cst.impl.analysis.PropertyImpl;
import org.e2immu.language.cst.impl.analysis.ValueImpl;
import org.e2immu.util.internal.graph.G;
import org.e2immu.util.internal.graph.op.Linearize;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

public class ShallowAnalyzer {
    private final AnnotatedApiParser annotatedApiParser;
    private List<TypeInfo> allTypes;
    private G<TypeInfo> graph;
    private List<TypeInfo> sorted;
    private final TypeInfo modifiedTi;
    private final TypeInfo notModifiedTi;
    private final List<Message> messages = new LinkedList<>();

    public ShallowAnalyzer(AnnotatedApiParser annotatedApiParser) {
        this.annotatedApiParser = annotatedApiParser;
        this.modifiedTi = annotatedApiParser.runtime().getFullyQualified(Modified.class, true);
        this.notModifiedTi = annotatedApiParser.runtime().getFullyQualified(NotModified.class, true);
    }

    public List<TypeInfo> go() {
        ShallowTypeAnalyzer shallowTypeAnalyzer = new ShallowTypeAnalyzer(annotatedApiParser);
        ShallowMethodAnalyzer shallowMethodAnalyzer = new ShallowMethodAnalyzer(annotatedApiParser);
        List<TypeInfo> types = annotatedApiParser.types();
        allTypes = types.stream().flatMap(TypeInfo::recursiveSubTypeStream)
                .filter(TypeInfo::isPublic)
                .flatMap(t -> Stream.concat(Stream.of(t), t.recursiveSuperTypeStream()))
                .distinct()
                .toList();
        G.Builder<TypeInfo> graphBuilder = new G.Builder<>(Long::sum);
        for (TypeInfo typeInfo : allTypes) {
            List<TypeInfo> allSuperTypes = typeInfo.recursiveSuperTypeStream()
                    .filter(TypeInfo::isPublic)
                    .toList();
            graphBuilder.add(typeInfo, allSuperTypes);
        }
        graph = graphBuilder.build();
        Linearize.Result<TypeInfo> linearize = Linearize.linearize(graph, Linearize.LinearizationMode.ALL);
        sorted = linearize.asList(Comparator.comparing(TypeInfo::fullyQualifiedName));
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.analyze(typeInfo);
        }
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.analyzeFields(typeInfo);
            AnnotationCounts ac = countAnnotations(typeInfo);
            boolean typeIsMutable = typeInfo.analysis()
                    .getOrDefault(PropertyImpl.IMMUTABLE_TYPE, ValueImpl.ImmutableImpl.MUTABLE).isMutable();
            boolean defaultModifiedMethod = typeIsMutable && ac.notModifiedOnMethod > 0;
            if (ac.modifiedOnMethod > 0 && ac.notModifiedOnMethod > 0) {
                messages.add(MessageImpl.warn(typeInfo,
                        "Mixing @NotModified and @Modified methods; default to @Modified"));
            }
            boolean defaultModifiedParameters = ac.notModifiedOnParameter > 0;
            if (ac.modifiedOnParameter > 0 && ac.notModifiedOnParameter > 0) {
                messages.add(MessageImpl.warn(typeInfo,
                        "Mixing @NotModified and @Modified on parameters; default to @Modified"));
            }
            typeInfo.constructorAndMethodStream()
                    .filter(MethodInfo::isPublic)
                    .forEach(mi -> shallowMethodAnalyzer.analyze(mi, defaultModifiedMethod, defaultModifiedParameters));
        }
        for (TypeInfo typeInfo : sorted) {
            shallowTypeAnalyzer.check(typeInfo);
        }
        messages.addAll(shallowMethodAnalyzer.messages());
        return sorted;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<TypeInfo> getAllTypes() {
        return allTypes;
    }

    public G<TypeInfo> getGraph() {
        return graph;
    }

    public List<TypeInfo> getSorted() {
        return sorted;
    }

    static class AnnotationCounts {
        int modifiedOnMethod;
        int notModifiedOnMethod;
        int modifiedOnParameter;
        int notModifiedOnParameter;
    }

    private AnnotationCounts countAnnotations(TypeInfo typeInfo) {
        AnnotationCounts ac = new AnnotationCounts();
        typeInfo.constructorAndMethodStream().filter(MethodInfo::isPublic).forEach(mi -> {
            for (AnnotationExpression ae : annotatedApiParser.annotations(mi)) {
                if (ae.typeInfo().equals(modifiedTi)) {
                    ac.modifiedOnMethod++;
                } else if (ae.typeInfo().equals(notModifiedTi)) {
                    ac.notModifiedOnMethod++;
                }
            }
            for (ParameterInfo pi : mi.parameters()) {
                for (AnnotationExpression ae : annotatedApiParser.annotations(pi)) {
                    if (ae.typeInfo().equals(modifiedTi)) {
                        ac.modifiedOnParameter++;
                    } else if (ae.typeInfo().equals(notModifiedTi)) {
                        ac.notModifiedOnParameter++;
                    }
                }
            }
        });
        return ac;
    }

    public List<Message> messages() {
        return messages;
    }
}
