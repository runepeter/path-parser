package org.brylex.parser.processor;

import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public final class HandlerCodeGenerator {

    private final ProcessingEnvironment env;

    public HandlerCodeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    /** Convert an arbitrary path segment into a valid Java identifier fragment. */
    private static String toJavaIdent(String segment) {
        StringBuilder sb = new StringBuilder();
        for (char c : segment.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    public void generate(HandlerModel model) throws IOException {
        // Use the TypeElement directly so JavaPoet generates correct nested-class notation (Outer.Inner)
        ClassName handlerClass = ClassName.get(model.handlerType());
        ClassName parseNode = ClassName.get("org.brylex.parser", "ParseNode");
        ClassName factoryIface = ClassName.get("org.brylex.parser", "PathParserFactory");
        ClassName invokerSet = ClassName.get("org.brylex.parser", "InvokerSet");
        ClassName textInvoker = ClassName.get("org.brylex.parser", "TextInvoker");

        // Build buildTree() method
        MethodSpec.Builder buildTree = MethodSpec.methodBuilder("buildTree")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(parseNode)
                .addStatement("$T root = new $T($S, null, null)", parseNode, parseNode, "/");

        java.util.Set<String> declaredVars = new java.util.LinkedHashSet<>();
        for (Binding b : model.bindings()) {
            if (!(b instanceof Binding.FieldText ft)) continue;
            String[] segments = ft.path().split("/");
            String parent = "root";
            StringBuilder varName = new StringBuilder("n");
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                varName.append("_").append(toJavaIdent(segment));
                String var = varName.toString();
                if (declaredVars.add(var)) {
                    buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                            parseNode, var, parent, segment);
                }
                parent = var;
            }
            buildTree.addStatement("$L.needsText = true", parent);
        }
        buildTree.addStatement("return root");

        // handlerType()
        MethodSpec handlerTypeMethod = MethodSpec.methodBuilder("handlerType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)))
                .addStatement("return $T.class", handlerClass)
                .build();

        // tree()
        MethodSpec treeMethod = MethodSpec.methodBuilder("tree")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(parseNode)
                .addStatement("return TREE")
                .build();

        // bind(...)
        ParameterizedTypeName subHandlerFactoryType = ParameterizedTypeName.get(
                ClassName.get(Function.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)),
                ClassName.get(Object.class));
        ParameterizedTypeName subFactoryLookupType = ParameterizedTypeName.get(
                ClassName.get(Function.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)),
                factoryIface);

        MethodSpec.Builder bind = MethodSpec.methodBuilder("bind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(invokerSet)
                .addParameter(Object.class, "handler")
                .addParameter(ParameterSpec.builder(subHandlerFactoryType, "subHandlerFactory").build())
                .addParameter(ParameterSpec.builder(subFactoryLookupType, "subFactoryLookup").build())
                .addStatement("$T h = ($T) handler", handlerClass, handlerClass);

        for (Binding b : model.bindings()) {
            if (!(b instanceof Binding.FieldText ft)) continue;
            String[] segments = ft.path().split("/");
            StringBuilder lookup = new StringBuilder("TREE");
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                lookup.append(".lookupChild(\"").append(segment).append("\", 0, null, null)");
            }
            bind.addStatement("$L.endInvokers.add(new $T(text -> h.$L = text))",
                    lookup.toString(), textInvoker, ft.fieldName());
        }
        bind.addStatement("return new $T(handler, $T.of())", invokerSet, Map.class);

        TypeSpec spec = TypeSpec.classBuilder(model.generatedClassName())
                .addJavadoc("Generated by path-parser-processor — do not edit manually\n")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(factoryIface)
                .addField(FieldSpec.builder(parseNode, "TREE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("buildTree()")
                        .build())
                .addMethod(buildTree.build())
                .addMethod(handlerTypeMethod)
                .addMethod(treeMethod)
                .addMethod(bind.build())
                .build();

        JavaFile.builder(model.packageName(), spec).build().writeTo(env.getFiler());
    }
}
