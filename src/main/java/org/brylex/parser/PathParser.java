package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.brylex.util.Tree;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;

public class PathParser {

    private final Stack<StringBuilder> characterStack;
    private final Tree<Node> tree;

    public PathParser(Object handler) {
        this(new Tree<Node>(new Node("/", NodeType.START_DOCUMENT)), handler);
    }

    PathParser(Tree<Node> tree, Object handler) {
        this.characterStack = new Stack<>();
        this.tree = tree;

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

        final LinkedList<String> nodes = new LinkedList<>(Arrays.asList(path.value().split("/")));
        final String leafNode = nodes.removeLast();
        final Tree<Node> trunk = buildTrunk(nodes);

        Node node = new Node(leafNode, NodeType.END_ELEMENT);
        FieldInvoker invoker = new FieldInvoker(field, handler);
        applyInvoker(trunk, node, invoker);
    }

    private void apply(Path path, Method method, Object handler) {

        final LinkedList<String> nodes = new LinkedList<>(Arrays.asList(path.value().split("/")));
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

        } else {

            Node createNode = new Node(leafNode, NodeType.START_ELEMENT);
            CreateInstanceInvoker createInstanceInvoker = new CreateInstanceInvoker(parameterType);
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

    private Tree<Node> buildTrunk(LinkedList<String> nodes) {
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

        final Stack<StartElement> stack = new Stack<>();

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
                    case XMLStreamConstants.START_ELEMENT:

                        stack.push(event.asStartElement());

                        balance++;

                        characterStack.push(new StringBuilder());

                        if (ignore == 0) {
                            Tree<Node> t = invokeStartElementHandlers(parseTree, event.asStartElement());
                            if (t == null) {

                                Node node = new Node(event);
                                if (parseTree.getTree(node) == null) {
                                    ignore++;
                                }

                            } else {
                                parseTree = t;
                            }
                        } else {
                            ignore++;
                        }

                        break;

                    case XMLStreamConstants.END_ELEMENT:

                        StartElement startElement = stack.pop();
                        if (!startElement.getName().equals(event.asEndElement().getName())) {
                            throw new IllegalStateException("Unexpected END element [" + event.asEndElement() + "].");
                        }

                        balance--;

                        StringBuilder stringBuilder = characterStack.pop();

                        if (ignore > 0) {

                            ignore--;

                        }  else {
                            Tree<Node> t = invokeFieldHandlers(parseTree, stringBuilder.toString(), event.asEndElement(), startElement);
                            parseTree = t != null ? t : parseTree;
                        }

                        break;

                    case XMLStreamConstants.START_DOCUMENT:

                        parseTree = tree.getTree(new Node("/", NodeType.START_DOCUMENT));

                        break;
                    case XMLStreamConstants.END_DOCUMENT:
                        break;
                    case XMLStreamConstants.CHARACTERS:

                        characterStack.peek().append(event.asCharacters().getData());

                        break;
                    default:
                        System.out.println("Event: [" + event + "]");
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    private Tree<Node> invokeStartElementHandlers(Tree<Node> parseTree, StartElement startElement) {

        String elementName = startElement.getName().getLocalPart();
        Tree<Node> subTree = parseTree.getTree(new Node(elementName, NodeType.START_ELEMENT));

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

        Tree<Node> subTree = parseTree.getTree(new Node(startElement));

        if (subTree != null) {
            subTree.getHead().invoke(fieldValue);
            subTree.getHead().invoke(endElement);

            return subTree;
        }

        return null;
    }

}
