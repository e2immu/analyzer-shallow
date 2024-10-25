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


import org.e2immu.language.cst.api.element.CompilationUnit;
import org.e2immu.language.cst.api.expression.Expression;
import org.e2immu.language.cst.api.info.*;
import org.e2immu.language.cst.api.output.Formatter;
import org.e2immu.language.cst.api.output.OutputBuilder;
import org.e2immu.language.cst.api.runtime.Runtime;
import org.e2immu.language.cst.api.statement.Block;
import org.e2immu.language.cst.api.statement.Statement;
import org.e2immu.language.cst.api.type.ParameterizedType;
import org.e2immu.language.cst.api.type.TypeParameter;
import org.e2immu.language.cst.print.FormatterImpl;
import org.e2immu.language.cst.print.FormattingOptionsImpl;
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
    private final String destinationPackage;
    private final Predicate<Info> predicate;

    public Composer(Runtime runtime,
                    String destinationPackage,
                    Predicate<Info> predicate) {
        this.runtime = runtime;
        this.destinationPackage = destinationPackage;
        this.predicate = predicate;
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

        for (TypeInfo subType : typeInfo.subTypes()) {
            appendType(newType, subType, false);
        }
        for (FieldInfo fieldInfo : typeInfo.fields()) {
            if (fieldInfo.access().isPublic() && predicate.test(fieldInfo)) {
                newType.builder().addField(createField(fieldInfo, newType));
            }
        }
        for (MethodInfo constructor : typeInfo.constructors()) {
            if (predicate.test(constructor)) {
                newType.builder().addMethod(createMethod(constructor, newType));
            }
        }
        for (MethodInfo methodInfo : typeInfo.methods()) {
            if (predicate.test(methodInfo)) {
                newType.builder().addMethod(createMethod(methodInfo, newType));
            }
        }
        newType.builder().commit();
        parentType.builder().addSubType(newType);
    }

    private boolean acceptTypeOrAnySubType(TypeInfo typeInfo) {
        if (predicate.test(typeInfo)) return true;
        return typeInfo.subTypes().stream().anyMatch(this::acceptTypeOrAnySubType);
    }

    private FieldInfo createField(FieldInfo fieldInfo, TypeInfo owner) {
        FieldInfo newField = runtime.newFieldInfo(fieldInfo.name(), fieldInfo.isStatic(), fieldInfo.type(), owner);
        fieldInfo.modifiers()
                .stream().filter(m -> !m.isPublic()).forEach(newField.builder()::addFieldModifier);
        if (fieldInfo.isFinal()) {
            newField.builder().setInitializer(runtime.nullValue(fieldInfo.type()));
        }
        newField.builder().setAccess(runtime.accessPackage()).commit();
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
        }
        ParameterizedType returnType = methodInfo.returnType();
        newMethod.builder()
                .setAccess(runtime.accessPackage())
                .setReturnType(returnType);
        if (methodInfo.hasReturnValue()) {
            Expression defaultReturnValue = runtime.nullValue(returnType);
            Statement returnStatement = runtime.newReturnStatement(defaultReturnValue);
            Block block = runtime.newBlockBuilder().addStatement(returnStatement).build();
            newMethod.builder().setMethodBody(block);
        } else {
            newMethod.builder().setMethodBody(runtime.newBlockBuilder().build());
        }
        for (ParameterInfo p : methodInfo.parameters()) {
            ParameterInfo pi = newMethod.builder().addParameter(p.name(), p.parameterizedType());
            pi.builder().setVarArgs(p.isVarArgs()).commit();
        }
        for (TypeParameter tp : methodInfo.typeParameters()) {
            TypeParameter newTp = runtime.newTypeParameter(tp.getIndex(), tp.simpleName(), newMethod);
            newMethod.builder().addTypeParameter(newTp);
        }
        if (methodInfo.isOverloadOfJLOMethod()) {
            LOGGER.info("Method {} is overload", methodInfo);
            if ("clone".equals(methodInfo.name()) || "finalize".equals(methodInfo.name())) {
                newMethod.builder().addMethodModifier(runtime.methodModifierProtected()).setAccess(runtime.accessProtected());
            } else {
                newMethod.builder().addMethodModifier(runtime.methodModifierPublic()).setAccess(runtime.accessPublic());
            }
        }
        newMethod.builder().commitParameters().commit();
        return newMethod;
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
        return typeInfo;
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
        return typeInfo;
    }

    static String convertToCamelCase(String packageName) {
        String[] components = packageName.split("\\.");
        return Arrays.stream(components).map(StringUtil::capitalize).collect(Collectors.joining());
    }

    public void write(Collection<TypeInfo> apiTypes, String writeAnnotatedAPIsDir) throws IOException {
        File base = new File(writeAnnotatedAPIsDir);
        if (base.mkdirs()) {
            LOGGER.info("Created annotated API destination folder '{}'", base.getAbsolutePath());
        }
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
            try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(outputFile),
                    StandardCharsets.UTF_8)) {
                OutputBuilder outputBuilder = apiType.print(runtime.qualificationQualifyFromPrimaryType());
                outputStreamWriter.write(formatter.write(outputBuilder));
            }
            LOGGER.info("Wrote {}", apiType);
            ++count;
        }
        LOGGER.info("Wrote {} files", count);
    }
}
