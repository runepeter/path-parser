package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.brylex.util.Tree;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Function;

public class PathParser {

    private final Deque<StringBuilder> characterStack;
    private final Tree<Node> tree;
    private final Function<Class<?>, Object> factory;

    public PathParser(Object handler) {
        this(handler, PathParser::defaultFactory);
    }

    public PathParser(Object handler, Function<Class<?>, Object> factory) {
        this(new Tree<Node>(new Node("/", NodeType.START_DOCUMENT)), handler, factory);
    }

    PathParser(Tree<Node> tree, Object handler, Function<Class<?>, Object> factory) {
        this.characterStack = new ArrayDeque<>();
        this.tree = tree;
        this.factory = factory;

        Method[] methods = handler.getClass().getDeclaredMethods();
        for (Method method : methods) {

            Path path = method.getAnnotation(Path.class);
            if (path != null) {
                apply(path, method, handler);
            }
        }

        for (Field field : handler.getClass().getDeclaredFields()) {

            Path path = field.getAnnotation(Path.class);
            if (path != null) {
                apply(path, field, handler);
            }
        }
    }

    private void apply(Path path, Field field, Object handler) {

        final Deque<String> nodes = new ArrayDeque<>(Arrays.asList(path.value().split("/")));
        final String leafNode = nodes.removeLast();

        if (leafNode.startsWith("@")) {
            String attributeName = leafNode.substring(1);
            final Tree<Node> trunk = buildTrunk(nodes);
            trunk.getHead().add(new AttributeInvoker(attributeName, field, handler));
            return;
        }

        final Tree<Node> trunk = buildTrunk(nodes);

        Class<?> elementType = FieldInvoker.elementTypeOf(field);
        if (elementType != null && !Conversions.canConvert(elementType)) {

            Node createNode = new Node(leafNode, NodeType.START_ELEMENT);
            CreateInstanceInvoker createInstanceInvoker = new CreateInstanceInvoker(elementType, factory);
            applyInvoker(trunk, createNode, createInstanceInvoker);

            Node applyNode = new Node(leafNode, NodeType.END_ELEMENT);
            Invoker subParserInvoker = new ApplySubParserInvoker(new FieldInvoker(field, handler), createInstanceInvoker);
            applyInvoker(trunk, applyNode, subParserInvoker);
            return;
        }

        Node node = new Node(leafNode, NodeType.END_ELEMENT);
        FieldInvoker invoker = new FieldInvoker(field, handler);
        applyInvoker(trunk, node, invoker);
    }

    private void apply(Path path, Method method, Object handler) {

        final Deque<String> nodes = new ArrayDeque<>(Arrays.asList(path.value().split("/")));
        final String leafNode = nodes.removeLast();
        final Tree<Node> trunk = buildTrunk(nodes);

        Class<?> parameterType = method.getParameterTypes()[0];
        if (StartElement.class.isAssignableFrom(parameterType)) {

            Node node = new Node(leafNode, NodeType.START_ELEMENT);
            MethodInvoker invoker = new MethodInvoker(method, handler);
            applyInvoker(trunk, node, invoker);

        } else if (EndElement.class.isAssignableFrom(parameterType)) {

            Node node = new Node(leafNode, NodeType.END_ELEMENT);
            MethodInvoker invoker = new MethodInvoker(method, handler);
            applyInvoker(trunk, node, invoker);

        } else if (Conversions.canConvert(parameterType)) {

            Node node = new Node(leafNode, NodeType.END_ELEMENT);
            MethodInvoker invoker = new MethodInvoker(method, handler);
            applyInvoker(trunk, node, invoker);

        } else {

            Node createNode = new Node(leafNode, NodeType.START_ELEMENT);
            CreateInstanceInvoker createInstanceInvoker = new CreateInstanceInvoker(parameterType, factory);
            applyInvoker(trunk, createNode, createInstanceInvoker);

            Node applyNode = new Node(leafNode, NodeType.END_ELEMENT);
            Invoker subParserInvoker = new ApplySubParserInvoker(new MethodInvoker(method, handler), createInstanceInvoker);
            applyInvoker(trunk, applyNode, subParserInvoker);
        }
    }

    private void applyInvoker(Tree<Node> trunk, Node node, Invoker invoker) {
        Tree<Node> t = trunk.getTree(node);
        if (t == null) {
            node.add(invoker);
            trunk.addLeaf(node);
        } else {
            t.getHead().add(invoker);
        }
    }

    private Tree<Node> buildTrunk(Deque<String> nodes) {
        Tree<Node> parent = tree;
        for (String step : nodes) {

            if (step.length() == 0) {
                continue;
            }

            Node node = new Node(step, NodeType.START_ELEMENT);

            Tree<Node> t = parent.getTree(node);
            if (t == null) {
                parent = parent.addLeaf(node);
            } else {
                parent = t;
            }
        }
        return parent;
    }

    private XMLEventReader xmlEventReader;

    public void parse(XMLEventReader reader) {

        this.xmlEventReader = reader;

        Tree<Node> parseTree = tree;

        final Deque<StartElement> stack = new ArrayDeque<>();

        try {

            if (!reader.peek().isStartDocument()) {
                parseTree = tree.getTree(new Node("/", NodeType.START_ELEMENT));
            }

            int balance = 0;
            int ignore = 0;

            while (reader.hasNext()) {

                XMLEvent event = reader.peek();
                if (event.getEventType() == XMLStreamConstants.END_ELEMENT && balance == 0) {
                    return;
                } else {
                    reader.nextEvent();
                }

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        StartElement startElement = event.asStartElement();
                        stack.push(startElement);
                        balance++;
                        characterStack.push(new StringBuilder());

                        if (ignore == 0) {
                            Tree<Node> t = invokeStartElementHandlers(parseTree, startElement);
                            if (t == null) {
                                if (lookupChild(parseTree, startElement, NodeType.END_ELEMENT) == null) {
                                    ignore++;
                                }
                            } else {
                                parseTree = t;
                            }
                        } else {
                            ignore++;
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        EndElement endElement = event.asEndElement();
                        StartElement startElement = stack.pop();
                        if (!startElement.getName().equals(endElement.getName())) {
                            throw new IllegalStateException("Unexpected END element [" + endElement + "].");
                        }
                        balance--;
                        StringBuilder stringBuilder = characterStack.pop();

                        if (ignore > 0) {
                            ignore--;
                        } else {
                            Tree<Node> t = invokeFieldHandlers(parseTree, stringBuilder.toString(), endElement, startElement);
                            parseTree = t != null ? t : parseTree;
                        }
                    }
                    case XMLStreamConstants.START_DOCUMENT ->
                            parseTree = tree.getTree(new Node("/", NodeType.START_DOCUMENT));
                    case XMLStreamConstants.END_DOCUMENT -> {
                    }
                    case XMLStreamConstants.CHARACTERS ->
                            characterStack.peek().append(event.asCharacters().getData());
                    default -> System.out.println("Event: [" + event + "]");
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    private Tree<Node> invokeStartElementHandlers(Tree<Node> parseTree, StartElement startElement) {

        Tree<Node> subTree = lookupChild(parseTree, startElement, NodeType.START_ELEMENT);

        if (subTree != null) {

            Node head = subTree.getHead();

            for (Invoker invoker : head.getInvokers()) {
                if (invoker instanceof CreateInstanceInvoker) {
                    invoker.invoke(xmlEventReader);
                } else {
                    invoker.invoke(startElement);
                }
            }

            return subTree;

        }

        return null;
    }

    private Tree<Node> invokeFieldHandlers(Tree<Node> parseTree, String fieldValue, EndElement endElement, StartElement startElement) {

        Tree<Node> subTree = lookupChild(parseTree, startElement, NodeType.END_ELEMENT);

        if (subTree != null) {
            subTree.getHead().invoke(fieldValue);
            subTree.getHead().invoke(endElement);

            return subTree;
        }

        return null;
    }

    private Tree<Node> lookupChild(Tree<Node> parseTree, StartElement startElement, NodeType type) {
        String name = startElement.getName().getLocalPart();
        Iterator<Attribute> attributes = startElement.getAttributes();
        while (attributes.hasNext()) {
            Attribute attribute = attributes.next();
            Node candidate = new Node(name, type, attribute.getName().getLocalPart(), attribute.getValue());
            Tree<Node> match = parseTree.getTree(candidate);
            if (match != null) {
                return match;
            }
        }
        return parseTree.getTree(new Node(name, type));
    }

    private static Object defaultFactory(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to instantiate [" + type + "].", e);
        }
    }

}
