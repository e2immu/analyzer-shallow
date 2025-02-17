/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyzer.shallow.analyzer;


import org.e2immu.language.cst.api.element.Comment;
import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.expression.AnnotationExpression;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.output.Formatter;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.api.output.Qualification;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.impl.element.SingleLineComment;
import org.e2immu.language.cst.impl.info.TypePrinter;
import org.e2immu.language.cst.print.FormatterImpl;
import org.e2immu.language.cst.print.FormattingOptionsImpl;
import org.e2immu.language.inspection.api.resource.InputPathEntry;
import org.e2immu.util.internal.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/*
Given a number of types, compose one annotated API "file" per package, in the style of the JavaUtil, JavaLang classes.
The file is generated in the form of a TypeInfo object, which can be output.

The general structure is

public class NameOfPackageWithoutDots {
    final static String PACKAGE_NAME = "name.of.package.without.dots";

    // the $ means that we'll relocate towards PACKAGE_NAME

    public static class/public interface Type1Name$ {
        public methods or constructors
            methods return { null } or the correct primary type
    }
    ...
    public static class/public interface Type2Name$ {

        public class InnerType { // doesn't need a $ anymore

        }
    }
 }


  new for 202502
  - unconfirmed annotations: //? @...
  - confirmed annotations: //@...
  - override annotations (different from computation): uncommented
  this works fine for type, field, method, but not so much for parameters.

  the step from unconfirmed to confirmed: user removes "? "
  the only difference between confirmed and unconfirmed is that confirmed ones are excluded from the frequency ** system.
 */
public class Composer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Composer.class);
    private final Runtime runtime;
    private final String destinationPackage;
    private final Predicate<Info> predicate;
    private final Map<Info, Integer> frequencyTable;
    private final Map<Info, List<AnnotationExpression>> annotationOverrides;
    private final Map<String, List<InputPathEntry>> pathEntriesPerPackage;

    private final Map<Info, Info> translateFromDollarToReal = new HashMap<>();
    private final TreeMap<Integer, Integer> starBounds;
    private final AnnotationHelper annotationHelper;

    public Composer(Runtime runtime,
                    String destinationPackage,
                    Map<Info, Integer> frequencyTable,
                    Map<Info, List<AnnotationExpression>> annotationOverrides,
                    Map<String, List<InputPathEntry>> pathEntriesPerPackage,
                    Predicate<Info> predicate) {
        this.runtime = runtime;
        this.destinationPackage = destinationPackage;
        this.predicate = predicate;
        this.frequencyTable = frequencyTable;
        this.annotationOverrides = annotationOverrides;
        this.pathEntriesPerPackage = pathEntriesPerPackage;
        starBounds = computeStarBounds(frequencyTable, annotationOverrides);
        annotationHelper = new AnnotationHelper(runtime);
    }

    private TreeMap<Integer, Integer> computeStarBounds(Map<Info, Integer> frequencyTable,
                                                        Map<Info, List<AnnotationExpression>> annotationOverrides) {
        if (frequencyTable == null || annotationOverrides == null) return new TreeMap<>();
        int[] frequenciesSorted = frequencyTable.entrySet().stream()
                .filter(e -> !annotationOverrides.containsKey(e.getKey()))
                .mapToInt(Map.Entry::getValue).sorted().toArray();
        TreeMap<Integer, Integer> map = new TreeMap<>();
        if (frequenciesSorted.length <= 5) {
            for (int i = 0; i < frequenciesSorted.length; i++) map.put(frequenciesSorted[i], i);
        } else {
            int n = frequenciesSorted.length;
            map.put((n * 99) / 100, 5); // 99%
            map.put((n * 95) / 100, 4); // 95%
            map.put((n * 9) / 10, 3);   // 90%
            map.put((n * 75) / 100, 2); // 75%
            map.put((n * 5) / 10, 1);   // 50%
        }
        return map;
    }

    public Collection<TypeInfo> compose(Collection<TypeInfo> primaryTypes) {
        Map<String, TypeInfo> typesPerPackage = new HashMap<>();
        for (TypeInfo primaryType : primaryTypes) {
            if (acceptTypeOrAnySubType(primaryType)) {
                assert primaryType.isPrimaryType();
                String packageName = primaryType.packageName();
                TypeInfo packageType = typesPerPackage.computeIfAbsent(packageName, this::newPackageType);
                appendType(packageType, primaryType, true);
            }
        }
        List<TypeInfo> allTypes = typesPerPackage.values().stream().toList();
        allTypes.forEach(t -> t.builder().commit());
        return allTypes;
    }

    private void appendType(TypeInfo parentType, TypeInfo typeInfo, boolean topLevel) {
        if (!acceptTypeOrAnySubType(typeInfo)) return;
        TypeInfo newType = createType(parentType, typeInfo, topLevel);
        translateFromDollarToReal.put(newType, typeInfo);

        newType.builder().addComment(addInformationLine(typeInfo));

        for (TypeInfo subType : typeInfo.subTypes()) {
            appendType(newType, subType, false);
        }
        for (FieldInfo fieldInfo : typeInfo.fields()) {
            if (fieldInfo.access().isPublic() && predicate.test(fieldInfo)) {
                FieldInfo newField = createField(fieldInfo, newType);
                translateFromDollarToReal.put(newField, fieldInfo);
                newType.builder().addField(newField);
            }
        }
        for (MethodInfo constructor : typeInfo.constructors()) {
            if (constructor.isPublic() && predicate.test(constructor)) {
                MethodInfo newConstructor = createMethod(constructor, newType);
                translateFromDollarToReal.put(newConstructor, constructor);
                newConstructor.parameters().forEach(newPi ->
                        translateFromDollarToReal.put(newPi, constructor.parameters().get(newPi.index())));
                newType.builder().addMethod(newConstructor);
            }
        }
        for (MethodInfo methodInfo : typeInfo.methods()) {
            if (methodInfo.isPublic() && predicate.test(methodInfo) && !methodInfo.isOverloadOfJLOMethod()) {
                MethodInfo newMethod = createMethod(methodInfo, newType);
                translateFromDollarToReal.put(newMethod, methodInfo);
                newMethod.parameters().forEach(newPi ->
                        translateFromDollarToReal.put(newPi, methodInfo.parameters().get(newPi.index())));
                newType.builder().addMethod(newMethod);
            }
        }
        newType.builder().commit();
        parentType.builder().addSubType(newType);
    }

    private Comment addInformationLine(MethodInfo methodInfo) {
        String shortString = "overrides in " + methodInfo.overrides()
                .stream().map(mi -> mi.typeInfo().fullyQualifiedName()).sorted().collect(Collectors.joining(", "))
                             + frequencyString(methodInfo);
        return new SingleLineComment(shortString);
    }

    private Comment addInformationLine(TypeInfo typeInfo) {
        String access = TypePrinter.minimalModifiers(typeInfo)
                .stream().map(m -> m.keyword().minimal())
                .collect(Collectors.joining(" ", "", " "));
        String type = typeInfo.typeNature().keyword().minimal() + " ";
        String extendString = typeInfo.parentClass() == null || typeInfo.parentClass().isJavaLangObject() ? ""
                : " extends " + typeInfo.parentClass().simpleString();
        String implementString = typeInfo.interfacesImplemented().isEmpty() ? ""
                : " implements " + typeInfo.interfacesImplemented().stream()
                .map(i -> i.print(runtime.qualificationQualifyFromPrimaryType(), false, runtime.diamondShowAll()).toString())
                .collect(Collectors.joining(", "));
        String shortString = access + type + typeInfo.simpleName() + extendString + implementString
                             + frequencyString(typeInfo);
        return new SingleLineComment(shortString);
    }

    private boolean acceptTypeOrAnySubType(TypeInfo typeInfo) {
        if (!typeInfo.isPubliclyAccessible()) return false;
        if (predicate.test(typeInfo)) return true;
        return typeInfo.subTypes().stream().anyMatch(this::acceptTypeOrAnySubType);
    }

    private FieldInfo createField(FieldInfo fieldInfo, TypeInfo owner) {
        FieldInfo newField = runtime.newFieldInfo(fieldInfo.name(), fieldInfo.isStatic(), fieldInfo.type(), owner);
        FieldInfo.Builder builder = newField.builder();
        fieldInfo.modifiers().stream().filter(m -> !m.isPublic()).forEach(builder::addFieldModifier);
        if (fieldInfo.isFinal()) {
            builder.setInitializer(runtime.nullValue(fieldInfo.type()));
        } else {
            builder.setInitializer(runtime.newEmptyExpression());
        }
        addAnnotationsOrComment(fieldInfo, builder);
        builder.setAccess(runtime.accessPackage()).commit();
        return newField;
    }

    private MethodInfo createMethod(MethodInfo methodInfo, TypeInfo owner) {
        MethodInfo newMethod;
        if (methodInfo.isConstructor()) {
            newMethod = runtime.newConstructor(owner);
        } else {
            MethodInfo.MethodType methodType = methodInfo.isStatic() ? runtime.methodTypeStaticMethod()
                    : runtime.methodTypeMethod();
            newMethod = runtime.newMethod(owner, methodInfo.name(), methodType);
            if (!methodInfo.overrides().isEmpty()) {
                newMethod.builder().addComment(addInformationLine(methodInfo));
            }
        }
        MethodInfo.Builder builder = newMethod.builder();
        addAnnotationsOrComment(methodInfo, builder);
        ParameterizedType returnType = methodInfo.returnType();
        builder
                .setAccess(runtime.accessPackage())
                .setReturnType(returnType);
        if (methodInfo.hasReturnValue()) {
            Expression defaultReturnValue = runtime.nullValue(returnType);
            Statement returnStatement = runtime.newReturnStatement(defaultReturnValue);
            Block block = runtime.newBlockBuilder().addStatement(returnStatement).build();
            builder.setMethodBody(block);
        } else {
            builder.setMethodBody(runtime.newBlockBuilder().build());
        }
        for (ParameterInfo p : methodInfo.parameters()) {
            ParameterInfo pi = builder.addParameter(p.name(), p.parameterizedType());
            addAnnotationsOrComment(pi, pi.builder());
            pi.builder().setVarArgs(p.isVarArgs()).commit();
        }
        for (TypeParameter tp : methodInfo.typeParameters()) {
            TypeParameter newTp = runtime.newTypeParameter(tp.getIndex(), tp.simpleName(), newMethod);
            builder.addTypeParameter(newTp);
        }
        if (methodInfo.isOverloadOfJLOMethod()) {
            LOGGER.info("Method {} is overload", methodInfo);
            if ("clone".equals(methodInfo.name()) || "finalize".equals(methodInfo.name())) {
                builder.addMethodModifier(runtime.methodModifierProtected()).setAccess(runtime.accessProtected());
            } else {
                builder.addMethodModifier(runtime.methodModifierPublic()).setAccess(runtime.accessPublic());
            }
        }
        builder.commitParameters().commit();
        return newMethod;
    }

    private void addAnnotationsOrComment(Info info, Info.Builder<?> builder) {
        List<AnnotationExpression> overrides = annotationOverrides.get(info);
        boolean haveOverrides = overrides != null && !overrides.isEmpty();
        if (haveOverrides) {
            builder.addAnnotations(overrides);
        } else {
            StringBuilder commentStringBuilder = new StringBuilder();
            if (overrides == null) commentStringBuilder.append("? ");
            Qualification qualification = runtime.qualificationQualifyFromPrimaryType();
            List<AnnotationExpression> toWrite = annotationHelper.annotationsToWrite(info);
            boolean first = true;
            for (AnnotationExpression ae : toWrite) {
                if (first) {
                    first = false;
                } else {
                    commentStringBuilder.append(" ");
                }
                commentStringBuilder.append(ae.print(qualification));
            }
            Comment comment;
            String commentString = commentStringBuilder.toString();
            if (info instanceof ParameterInfo) {
                comment = runtime.newMultilineComment(commentString);
            } else {
                comment = runtime.newSingleLineComment(commentString);
            }
            builder.addComment(comment);
        }
    }

    private TypeInfo createType(TypeInfo parent, TypeInfo typeToCopy, boolean topLevel) {
        String typeName = typeToCopy.simpleName();
        TypeInfo typeInfo = runtime.newTypeInfo(parent, topLevel ? typeName + "$" : typeName);
        typeInfo.builder().setParentClass(runtime.objectParameterizedType())
                .setTypeNature(runtime.typeNatureClass())
                .setAccess(runtime.accessPackage())
                .setSingleAbstractMethod(null);
        for (TypeParameter tp : typeToCopy.typeParameters()) {
            TypeParameter newTp = runtime.newTypeParameter(tp.getIndex(), tp.simpleName(), typeInfo);
            typeInfo.builder().addOrSetTypeParameter(newTp);
        }
        addAnnotationsOrComment(typeInfo, typeInfo.builder());
        return typeInfo;
    }

    public Map<Info, Info> translateFromDollarToReal() {
        return translateFromDollarToReal;
    }

    private TypeInfo newPackageType(String packageName) {
        String camelCasePackageName = convertToCamelCase(packageName);
        CompilationUnit compilationUnit = runtime.newCompilationUnitBuilder().setPackageName(destinationPackage).build();
        TypeInfo typeInfo = runtime.newTypeInfo(compilationUnit, camelCasePackageName);
        TypeInfo.Builder builder = typeInfo.builder();
        builder.setTypeNature(runtime.typeNatureClass())
                .setParentClass(runtime.objectParameterizedType())
                .addTypeModifier(runtime.typeModifierPublic())
                .setAccess(runtime.accessPublic())
                .setSingleAbstractMethod(null);
        FieldInfo packageField = runtime.newFieldInfo("PACKAGE_NAME", true,
                runtime.stringParameterizedType(), typeInfo);
        packageField.builder()
                .addFieldModifier(runtime.fieldModifierFinal())
                .addFieldModifier(runtime.fieldModifierStatic())
                .addFieldModifier(runtime.fieldModifierPublic())
                .setAccess(runtime.accessPublic())
                .setInitializer(runtime.newStringConstant(packageName))
                .commit();
        builder.addField(packageField);
        FieldInfo pathEntries = runtime.newFieldInfo("SOURCES", true,
                runtime.stringParameterizedType().copyWithArrays(1), typeInfo);
        List<Expression> strings = pathEntriesPerPackage.getOrDefault(packageName, List.of()).stream()
                .flatMap(pe -> Stream.concat(Stream.of((Expression) runtime.newStringConstant(pe.path())),
                        Stream.of(runtime.newStringConstant(pe.hash() == null ? "?" : pe.hash()))))
                .toList();
        pathEntries.builder()
                .addFieldModifier(runtime.fieldModifierFinal())
                .addFieldModifier(runtime.fieldModifierStatic())
                .addFieldModifier(runtime.fieldModifierPublic())
                .setAccess(runtime.accessPublic())
                .setInitializer(runtime.newArrayInitializerBuilder()
                        .setExpressions(strings)
                        .setCommonType(runtime.stringParameterizedType()).build())
                .commit();
        builder.addField(pathEntries);
        return typeInfo;
    }

    static String convertToCamelCase(String packageName) {
        String[] components = packageName.split("\\.");
        return Arrays.stream(components).map(StringUtil::capitalize).collect(Collectors.joining());
    }

    public void write(Collection<TypeInfo> apiTypes,
                      String writeAnnotatedAPIsDir,
                      Supplier<Qualification.Decorator> decoratorSupplier) throws IOException {
        File base = new File(writeAnnotatedAPIsDir);
        if (base.mkdirs()) {
            LOGGER.info("Created annotated API destination folder '{}'", base.getAbsolutePath());
        }
        write(apiTypes, base, decoratorSupplier);
    }

    public void write(Collection<TypeInfo> apiTypes,
                      File base,
                      Supplier<Qualification.Decorator> decoratorSupplier) throws IOException {
        Formatter formatter = new FormatterImpl(runtime, FormattingOptionsImpl.DEFAULT);
        int count = 0;
        for (TypeInfo apiType : apiTypes) {
            assert apiType.isPrimaryType() && apiType.hasBeenInspected();

            String convertedPackage = apiType.packageName().replace(".", "/");
            File directory = new File(base, convertedPackage);
            if (directory.mkdirs()) {
                LOGGER.info("Created annotated API destination package folder '{}'", directory.getAbsolutePath());
            }
            File outputFile = new File(directory, apiType.simpleName() + ".java");
            Qualification.Decorator decorator = decoratorSupplier.get();
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(outputFile),
                    StandardCharsets.UTF_8)) {
                Qualification qualification = runtime.qualificationQualifyFromPrimaryType(decorator);
                OutputBuilder outputBuilder = apiType.print(qualification);
                outputStreamWriter.write(formatter.write(outputBuilder));
            }
            LOGGER.info("Wrote {}", apiType);
            ++count;
        }
        LOGGER.info("Wrote {} files", count);
    }

    private String frequencyString(Info info) {
        if (frequencyTable == null || annotationOverrides == null) return "";
        if (annotationOverrides.containsKey(info)) return "";
        Integer freq = frequencyTable.get(info);
        if (freq == null || freq == 0) return "";
        Integer stars = starBounds.floorKey(freq);
        if (stars == null) return "";
        return "; freq " + ("*".repeat(stars));
    }
}
