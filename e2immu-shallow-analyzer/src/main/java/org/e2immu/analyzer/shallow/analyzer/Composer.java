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


import org.e2immu.language.cst.api.info.Info;
import org.e2immu.language.cst.api.info.TypeInfo;
import org.e2immu.language.cst.api.output.Formatter;
import org.e2immu.language.cst.api.output.FormattingOptions;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.print.FormatterImpl;
import org.e2immu.language.cst.print.FormattingOptionsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


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

- The purpose of this class is to generate an AnnotatedAPI file for others to start editing.
  This can be run on byte-code inspected Java, meaning the JavaParser needn't used, so we can do Java 16 already.

- Only public methods, types and fields will be shown.

 */
public class Composer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Composer.class);
    private final Runtime runtime;
    private final String destinationDirectory;
    private final Predicate<Info> predicate;

    public Composer(Runtime runtime, String destinationDirectory, Predicate<Info> predicate) {
        this.runtime = runtime;
        this.destinationDirectory = destinationDirectory;
        this.predicate = predicate;
    }

/*
    public Collection<TypeInfo> compose(Collection<TypeInfo> primaryTypes) {
        Map<String, TypeInspectionImpl.Builder> buildersPerPackage = new HashMap<>();
        for (TypeInfo primaryType : primaryTypes) {
            assert primaryType.isPrimaryType();
            String packageName = primaryType.packageName();
            TypeInspectionImpl.Builder builder = buildersPerPackage.computeIfAbsent(packageName,
                    this::newPackageTypeBuilder);
            appendType(primaryType, builder, true);
        }
        return buildersPerPackage.values().stream()
                .map(builder -> {
                    TypeInspection inspection = builder.setFunctionalInterface(null).build(typeMap);
                    TypeInfo typeInfo = inspection.typeInfo();
                    typeInfo.typeInspection.set(inspection);
                    return typeInfo;
                })
                .toList();
    }

    private void appendType(TypeInfo primaryType, TypeInspectionImpl.Builder packageBuilder, boolean topLevel) {
        if (!acceptTypeOrAnySubType(primaryType)) return;
        if (!primaryType.typeInspection.isSet()) {
            LOGGER.warn("Ignoring {} in composer, no type inspection", primaryType.fullyQualifiedName);
            return;
        }
        TypeInspection typeInspection = primaryType.typeInspection.get(primaryType.fullyQualifiedName);
        TypeInspectionImpl.Builder typeBuilder = newTypeBuilder(packageBuilder.typeInfo(), typeInspection, topLevel);

        for (TypeInfo subType : typeInspection.subTypes()) {
            appendType(subType, typeBuilder, false);
        }
        for (FieldInfo fieldInfo : typeInspection.fields()) {
            if (fieldInfo.fieldInspection.get().isPublic() && predicate.test(fieldInfo)) {
                typeBuilder.addField(createField(fieldInfo, typeBuilder.typeInfo()));
            }
        }
        for (MethodInfo constructor : typeInspection.constructors()) {
            if (predicate.test(constructor)) {
                typeBuilder.addMethod(createMethod(constructor, typeBuilder.typeInfo()));
            }
        }
        for (MethodInfo methodInfo : typeInspection.methods()) {
            if (predicate.test(methodInfo)) {
                typeBuilder.addMethod(createMethod(methodInfo, typeBuilder.typeInfo()));
            }
        }

        TypeInspection builtType = typeBuilder.setFunctionalInterface(null)
                .setAccess(Inspection.Access.PACKAGE)
                .build(typeMap);
        TypeInfo typeInfo = builtType.typeInfo();
        typeInfo.typeInspection.set(builtType);
        packageBuilder.addSubType(typeInfo);
    }

    private boolean acceptTypeOrAnySubType(TypeInfo typeInfo) {
        if (predicate().test(typeInfo)) return true;
        if (typeInfo.typeInspection.isSet()) {
            TypeInspection inspection = typeInfo.typeInspection.get(typeInfo.fullyQualifiedName);
            return inspection.subTypes().stream().anyMatch(this::acceptTypeOrAnySubType);
        }
        LOGGER.warn("Ignoring type {} in composer", typeInfo.fullyQualifiedName);
        return false;
    }

    private FieldInfo createField(FieldInfo fieldInfo, TypeInfo owner) {
        FieldInspection inspection = fieldInfo.fieldInspection.get();
        FieldInfo newField = new FieldInfo(fieldInfo.getIdentifier(), fieldInfo.type, fieldInfo.name, owner);
        FieldInspectionImpl.Builder builder = new FieldInspectionImpl.Builder(fieldInfo);
        inspection.getModifiers()
                .stream().filter(m -> m != FieldModifier.PUBLIC).forEach(builder::addModifier);
        if (inspection.getModifiers().contains(FieldModifier.FINAL)) {
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            builder.setInspectedInitialiserExpression(bestType == null ?
                    NullConstant.NULL_CONSTANT : ConstantExpression.nullValue(typeMap.getPrimitives(),
                    fieldInfo.getIdentifier(), bestType));
        }
        newField.fieldInspection.set(builder
                .setAccess(Inspection.Access.PACKAGE)
                .build(typeMap));
        return newField;
    }

    private MethodInfo createMethod(MethodInfo methodInfo, TypeInfo owner) {
        MethodInspection methodInspection = methodInfo.methodInspection.get();
        MethodInspectionImpl.Builder builder;
        if (methodInfo.isConstructor()) {
            builder = new MethodInspectionImpl.Builder(owner, MethodInfo.MethodType.CONSTRUCTOR);
        } else {
            builder = new MethodInspectionImpl.Builder(owner, methodInfo.name, MethodInfo.MethodType.METHOD);
        }

        if (methodInfo.isStatic()) builder.addModifier(MethodModifier.STATIC);

        ParameterizedType returnType = methodInspection.getReturnType();
        builder.setReturnType(returnType);
        if (methodInfo.hasReturnValue()) {
            Expression defaultReturnValue = ConstantExpression.nullValue(typeMap().getPrimitives(),
                    methodInfo.getIdentifier(), returnType.typeInfo);
            Statement returnStatement = new ReturnStatement(methodInfo.identifier, defaultReturnValue);
            Block block = new Block.BlockBuilder(Identifier.generate("compose block")).addStatement(returnStatement).build();
            builder.setInspectedBlock(block);
        }
        for (ParameterInfo p : methodInspection.getParameters()) {
            ParameterInspection.Builder newParameterBuilder = builder.newParameterInspectionBuilder
                    (p.identifier, p.parameterizedType, p.name, p.index);
            if (p.parameterInspection.get().isVarArgs()) {
                newParameterBuilder.setVarArgs(true);
            }
            builder.addParameter(newParameterBuilder);
        }
        MethodInfo newMethod = builder
                .setAccess(Inspection.Access.PACKAGE)
                .build(InspectionProvider.DEFAULT).getMethodInfo();
        newMethod.methodResolution.set(new MethodResolution.Builder().build());
        return newMethod;
    }

    private TypeInspectionImpl.Builder newTypeBuilder(TypeInfo packageType, TypeInspection typeToCopy, boolean topLevel) {
        String typeName = typeToCopy.typeInfo().simpleName;
        TypeInfo typeInfo = new TypeInfo(packageType, topLevel ? typeName + "$" : typeName);
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND_WITHOUT_STATEMENTS);
        builder.noParent(typeMap.getPrimitives())
                .setTypeNature(TypeNature.CLASS)
                .addTypeModifier(TypeModifier.STATIC);
        return builder;
    }

    private TypeInspectionImpl.Builder newPackageTypeBuilder(String packageName) {
        String camelCasePackageName = convertToCamelCase(packageName);
        TypeInfo typeInfo = new TypeInfo(destinationPackage, camelCasePackageName);
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(typeInfo, BY_HAND_WITHOUT_STATEMENTS);
        builder.noParent(typeMap.getPrimitives())
                .setTypeNature(TypeNature.CLASS)
                .addTypeModifier(TypeModifier.PUBLIC);
        FieldInfo packageField = new FieldInfo(Identifier.generate("PACKAGE NAME"), typeMap.getPrimitives().stringParameterizedType(),
                "PACKAGE_NAME", typeInfo);
        FieldInspectionImpl.Builder packageFieldInspectionBuilder = new FieldInspectionImpl.Builder(packageField);
        packageFieldInspectionBuilder
                .addModifier(FieldModifier.FINAL)
                .addModifier(FieldModifier.STATIC)
                .addModifier(FieldModifier.PUBLIC)
                .setAccess(Inspection.Access.PUBLIC)
                .setInspectedInitialiserExpression(new StringConstant(typeMap.getPrimitives(), packageName));
        packageField.fieldInspection.set(packageFieldInspectionBuilder.build(typeMap));
        builder.addField(packageField);
        return builder;
    }

    static String convertToCamelCase(String packageName) {
        String[] components = packageName.split("\\.");
        return Arrays.stream(components).map(StringUtil::capitalise).collect(Collectors.joining());
    }

    public void write(Collection<TypeInfo> apiTypes, String writeAnnotatedAPIsDir) throws IOException {
        File base = new File(writeAnnotatedAPIsDir);
        if (base.mkdirs()) {
            LOGGER.info("Created annotated API destination folder {}", base);
        }
        for (TypeInfo apiType : apiTypes) {
            OutputBuilder outputBuilder = apiType.print();
            Formatter formatter = new FormatterImpl(runtime, FormattingOptionsImpl.DEFAULT);

            String convertedPackage = apiType.packageName().replace(".", "/");
            File directory = new File(base, convertedPackage);
            if (directory.mkdirs()) {
                LOGGER.info("Created annotated API destination package folder {}", directory);
            }
            File outputFile = new File(directory, apiType.simpleName() + ".java");
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(outputFile),
                    StandardCharsets.UTF_8)) {
                outputStreamWriter.write(formatter.write(outputBuilder));
            }
        }
    }*/
}
