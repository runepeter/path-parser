package org.brylex.parser.processor;

import com.squareup.javapoet.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public final class HandlerCodeGenerator {

    private static final java.util.regex.Pattern FILTER_PATTERN =
            java.util.regex.Pattern.compile("(.+)\\[@(.+)=['\"]?([^'\"\\]]+)['\"]?\\]");

    /** Returns [elementName, filterAttrName|null, filterAttrValue|null]. */
    private static String[] parseSegment(String segment) {
        java.util.regex.Matcher m = FILTER_PATTERN.matcher(segment);
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2), m.group(3)};
        }
        return new String[]{segment, null, null};
    }

    private final ProcessingEnvironment env;

    public HandlerCodeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    /**
     * Returns the boxed ClassName for a field type string.
     * For primitives, returns the boxed java.lang type.
     * For reference types, looks up the TypeElement via the processing environment
     * so that JavaPoet handles nested-class notation correctly.
     */
    private ClassName boxedClassName(Binding.FieldText ft) {
        return boxedClassNameForType(ft.fieldType(), ft.element());
    }

    private ClassName boxedClassNameForAttr(Binding.Attribute attr) {
        return boxedClassNameForType(attr.fieldType(), attr.element());
    }

    private static String collectionInitFor(String type) {
        if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection"))
            return "new java.util.ArrayList<>()";
        if (type.startsWith("java.util.Set")) return "new java.util.LinkedHashSet<>()";
        if (type.startsWith("java.util.Queue")) return "new java.util.ArrayDeque<>()";
        throw new IllegalArgumentException("Ustøttet collection-type: " + type);
    }

    private static String boxedTypeLiteral(String typeName) {
        return switch (typeName) {
            case "int"     -> "java.lang.Integer";
            case "long"    -> "java.lang.Long";
            case "short"   -> "java.lang.Short";
            case "byte"    -> "java.lang.Byte";
            case "double"  -> "java.lang.Double";
            case "float"   -> "java.lang.Float";
            case "boolean" -> "java.lang.Boolean";
            case "char"    -> "java.lang.Character";
            default        -> typeName;
        };
    }

    private ClassName boxedClassNameForType(String fieldType, javax.lang.model.element.Element element) {
        return switch (fieldType) {
            case "int"     -> ClassName.get("java.lang", "Integer");
            case "long"    -> ClassName.get("java.lang", "Long");
            case "short"   -> ClassName.get("java.lang", "Short");
            case "byte"    -> ClassName.get("java.lang", "Byte");
            case "double"  -> ClassName.get("java.lang", "Double");
            case "float"   -> ClassName.get("java.lang", "Float");
            case "boolean" -> ClassName.get("java.lang", "Boolean");
            case "char"    -> ClassName.get("java.lang", "Character");
            default -> {
                // Reference type — resolve via TypeElement so JavaPoet handles
                // nested classes (e.g. OuterClass.InnerEnum) correctly.
                var typeElem = (javax.lang.model.element.TypeElement)
                        env.getTypeUtils().asElement(((javax.lang.model.element.VariableElement) element).asType());
                if (typeElem != null) {
                    yield ClassName.get(typeElem);
                }
                // Fallback: parse the FQ name (only for non-nested reference types)
                int lastDot = fieldType.lastIndexOf('.');
                if (lastDot < 0) {
                    yield ClassName.get("", fieldType);
                }
                yield ClassName.get(fieldType.substring(0, lastDot), fieldType.substring(lastDot + 1));
            }
        };
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
            if (b instanceof Binding.FieldText ft) {
                String[] segments = ft.path().split("/");
                String parent = "root";
                StringBuilder varName = new StringBuilder("n");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    String elemName = parsed[0];
                    String filterName = parsed[1];
                    String filterValue = parsed[2];
                    varName.append("_").append(toJavaIdent(elemName));
                    if (filterName != null) {
                        varName.append("__").append(toJavaIdent(filterName))
                               .append("_").append(toJavaIdent(filterValue));
                    }
                    String var = varName.toString();
                    if (declaredVars.add(var)) {
                        if (filterName == null) {
                            buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                                    parseNode, var, parent, elemName);
                        } else {
                            buildTree.addStatement("$T $L = $L.addChild($S, $S, $S)",
                                    parseNode, var, parent, elemName, filterName, filterValue);
                        }
                    }
                    parent = var;
                }
                buildTree.addStatement("$L.needsText = true", parent);
            } else if (b instanceof Binding.Attribute attr) {
                // Build nodes for the parent path (the element that carries the attribute)
                String[] segments = attr.path().split("/");
                String parent = "root";
                StringBuilder varName = new StringBuilder("n");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    String elemName = parsed[0];
                    String filterName = parsed[1];
                    String filterValue = parsed[2];
                    varName.append("_").append(toJavaIdent(elemName));
                    if (filterName != null) {
                        varName.append("__").append(toJavaIdent(filterName))
                               .append("_").append(toJavaIdent(filterValue));
                    }
                    String var = varName.toString();
                    if (declaredVars.add(var)) {
                        if (filterName == null) {
                            buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                                    parseNode, var, parent, elemName);
                        } else {
                            buildTree.addStatement("$T $L = $L.addChild($S, $S, $S)",
                                    parseNode, var, parent, elemName, filterName, filterValue);
                        }
                    }
                    parent = var;
                }
                buildTree.addStatement("$L.needsStartElement = true", parent);
            } else if (b instanceof Binding.Collection coll) {
                String[] segments = coll.path().split("/");
                String parent = "root";
                StringBuilder varName = new StringBuilder("n");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    String elemName = parsed[0];
                    String filterName = parsed[1];
                    String filterValue = parsed[2];
                    varName.append("_").append(toJavaIdent(elemName));
                    if (filterName != null) {
                        varName.append("__").append(toJavaIdent(filterName))
                               .append("_").append(toJavaIdent(filterValue));
                    }
                    String var = varName.toString();
                    if (declaredVars.add(var)) {
                        if (filterName == null) {
                            buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                                    parseNode, var, parent, elemName);
                        } else {
                            buildTree.addStatement("$T $L = $L.addChild($S, $S, $S)",
                                    parseNode, var, parent, elemName, filterName, filterValue);
                        }
                    }
                    parent = var;
                }
                buildTree.addStatement("$L.needsText = true", parent);
            } else if (b instanceof Binding.MethodText mt) {
                String[] segments = mt.path().split("/");
                String parent = "root";
                StringBuilder varName = new StringBuilder("n");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    String elemName = parsed[0];
                    String filterName = parsed[1];
                    String filterValue = parsed[2];
                    varName.append("_").append(toJavaIdent(elemName));
                    if (filterName != null) {
                        varName.append("__").append(toJavaIdent(filterName))
                               .append("_").append(toJavaIdent(filterValue));
                    }
                    String var = varName.toString();
                    if (declaredVars.add(var)) {
                        if (filterName == null) {
                            buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                                    parseNode, var, parent, elemName);
                        } else {
                            buildTree.addStatement("$T $L = $L.addChild($S, $S, $S)",
                                    parseNode, var, parent, elemName, filterName, filterValue);
                        }
                    }
                    parent = var;
                }
                buildTree.addStatement("$L.needsText = true", parent);
            } else if (b instanceof Binding.MethodEvent ev) {
                String[] segments = ev.path().split("/");
                String parent = "root";
                StringBuilder varName = new StringBuilder("n");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    String elemName = parsed[0];
                    String filterName = parsed[1];
                    String filterValue = parsed[2];
                    varName.append("_").append(toJavaIdent(elemName));
                    if (filterName != null) {
                        varName.append("__").append(toJavaIdent(filterName))
                               .append("_").append(toJavaIdent(filterValue));
                    }
                    String var = varName.toString();
                    if (declaredVars.add(var)) {
                        if (filterName == null) {
                            buildTree.addStatement("$T $L = $L.addChild($S, null, null)",
                                    parseNode, var, parent, elemName);
                        } else {
                            buildTree.addStatement("$T $L = $L.addChild($S, $S, $S)",
                                    parseNode, var, parent, elemName, filterName, filterValue);
                        }
                    }
                    parent = var;
                }
                if (ev.eventKind() == Binding.MethodEvent.EventKind.START) {
                    buildTree.addStatement("$L.needsStartElement = true", parent);
                } else {
                    buildTree.addStatement("$L.needsEndElement = true", parent);
                }
            }
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

        ClassName conversions = ClassName.get("org.brylex.parser", "Conversions");
        ClassName attributeBindingInvoker = ClassName.get("org.brylex.parser", "AttributeBindingInvoker");
        ClassName attributeSnapshot = ClassName.get("org.brylex.parser", "AttributeSnapshot");
        for (Binding b : model.bindings()) {
            if (b instanceof Binding.FieldText ft) {
                String[] segments = ft.path().split("/");
                StringBuilder lookup = new StringBuilder("TREE");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    if (parsed[1] == null) {
                        lookup.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
                    } else {
                        lookup.append(".lookupChildFiltered(\"").append(parsed[0])
                              .append("\", \"").append(parsed[1])
                              .append("\", \"").append(parsed[2]).append("\")");
                    }
                }
                if ("java.lang.String".equals(ft.fieldType())) {
                    bind.addStatement("$L.endInvokers.add(new $T(text -> h.$L = text))",
                            lookup.toString(), textInvoker, ft.fieldName());
                } else {
                    // Determine the boxed ClassName for the cast — JavaPoet uses $T to emit the
                    // simple name and generate the appropriate import automatically.
                    ClassName boxed = boxedClassName(ft);
                    bind.addStatement("$L.endInvokers.add(new $T(text -> h.$L = ($T) $T.convert(text, $T.class)))",
                            lookup.toString(), textInvoker, ft.fieldName(), boxed, conversions, boxed);
                }
            } else if (b instanceof Binding.Attribute attr) {
                // Build lookup chain for the parent path (element that carries the attribute)
                String[] segments = attr.path().split("/");
                StringBuilder lookup = new StringBuilder("TREE");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    if (parsed[1] == null) {
                        lookup.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
                    } else {
                        lookup.append(".lookupChildFiltered(\"").append(parsed[0])
                              .append("\", \"").append(parsed[1])
                              .append("\", \"").append(parsed[2]).append("\")");
                    }
                }
                if ("java.lang.String".equals(attr.fieldType())) {
                    bind.addStatement("$L.startInvokers.add(new $T($S, ($T snap, $T value) -> h.$L = value))",
                            lookup.toString(), attributeBindingInvoker, attr.attrName(),
                            attributeSnapshot, String.class, attr.fieldName());
                } else {
                    ClassName boxed = boxedClassNameForAttr(attr);
                    bind.addStatement("$L.startInvokers.add(new $T($S, ($T snap, $T value) -> h.$L = ($T) $T.convert(value, $T.class)))",
                            lookup.toString(), attributeBindingInvoker, attr.attrName(),
                            attributeSnapshot, String.class, attr.fieldName(), boxed, conversions, boxed);
                }
            } else if (b instanceof Binding.Collection coll) {
                String[] segments = coll.path().split("/");
                StringBuilder lookup = new StringBuilder("TREE");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    if (parsed[1] == null) {
                        lookup.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
                    } else {
                        lookup.append(".lookupChildFiltered(\"").append(parsed[0])
                              .append("\", \"").append(parsed[1])
                              .append("\", \"").append(parsed[2]).append("\")");
                    }
                }
                String elemType = coll.elementType();
                String boxedElem = boxedTypeLiteral(elemType);
                String initExpr = collectionInitFor(coll.collectionType());
                String addExpr = "java.lang.String".equals(elemType)
                        ? "text"
                        : "(" + boxedElem + ") org.brylex.parser.Conversions.convert(text, " + boxedElem + ".class)";
                boolean isFinal = coll.element().getModifiers()
                        .contains(javax.lang.model.element.Modifier.FINAL);
                if (isFinal) {
                    // Final field is pre-initialized by the user — just add to the existing collection.
                    bind.addStatement("$L.endInvokers.add(new $T(text -> { h.$L.add($L); }))",
                            lookup.toString(),
                            textInvoker,
                            coll.fieldName(), addExpr);
                } else {
                    bind.addStatement("$L.endInvokers.add(new $T(text -> { if (h.$L == null) h.$L = $L; h.$L.add($L); }))",
                            lookup.toString(),
                            textInvoker,
                            coll.fieldName(), coll.fieldName(), initExpr,
                            coll.fieldName(), addExpr);
                }
            } else if (b instanceof Binding.MethodText mt) {
                String[] segments = mt.path().split("/");
                StringBuilder lookup = new StringBuilder("TREE");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    if (parsed[1] == null) {
                        lookup.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
                    } else {
                        lookup.append(".lookupChildFiltered(\"").append(parsed[0])
                              .append("\", \"").append(parsed[1])
                              .append("\", \"").append(parsed[2]).append("\")");
                    }
                }
                String paramType = mt.paramType();
                String boxedType = boxedTypeLiteral(paramType);
                String callValue = "java.lang.String".equals(paramType)
                        ? "text"
                        : "(" + paramType + ") org.brylex.parser.Conversions.convert(text, " + boxedType + ".class)";
                bind.addStatement("$L.endInvokers.add(new $T(text -> h.$L($L)))",
                        lookup.toString(),
                        textInvoker,
                        mt.methodName(), callValue);
            } else if (b instanceof Binding.MethodEvent ev) {
                String[] segments = ev.path().split("/");
                StringBuilder lookup = new StringBuilder("TREE");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    String[] parsed = parseSegment(segment);
                    if (parsed[1] == null) {
                        lookup.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
                    } else {
                        lookup.append(".lookupChildFiltered(\"").append(parsed[0])
                              .append("\", \"").append(parsed[1])
                              .append("\", \"").append(parsed[2]).append("\")");
                    }
                }
                boolean isStart = ev.eventKind() == Binding.MethodEvent.EventKind.START;
                String invokerList = isStart ? "startInvokers" : "endInvokers";
                String kindEnum = isStart ? "START_ELEMENT" : "END_ELEMENT";
                String castType = isStart
                        ? "javax.xml.stream.events.StartElement"
                        : "javax.xml.stream.events.EndElement";
                bind.addStatement("$L.$L.add(new $T($T.Kind.$L, arg -> h.$L(($L) arg)))",
                        lookup.toString(), invokerList,
                        ClassName.get("org.brylex.parser", "EventInvoker"),
                        ClassName.get("org.brylex.parser", "EventInvoker"),
                        kindEnum,
                        ev.methodName(), castType);
            }
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
