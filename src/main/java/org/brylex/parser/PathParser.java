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
import java.util.LinkedList;
import java.util.Stack;

public class PathParser {

    private final Stack<StringBuilder> characterStack;
    private final Tree<Node> tree;

    public PathParser(Object... handlers) {

        this.characterStack = new Stack<>();
        this.tree = new Tree<Node>(new Node("/", NodeType.START_DOCUMENT));

        for (Object handler : handlers) {

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
    }


    private void apply(Path path, Field field, Object handler) {

        final LinkedList<String> nodes = new LinkedList<>(Arrays.asList(path.value().split("/")));
        final String leafNode = nodes.removeLast();

        Tree<Node> trunk = buildTrunk(nodes);

        Node node = new Node(leafNode, NodeType.END_ELEMENT);

        Tree<Node> t = trunk.getTree(node);
        if (t == null) {

            node.add(new FieldInvoker(field, handler));

            trunk.addLeaf(node);
        } else {
            t.getHead().add(new FieldInvoker(field, handler));
        }
    }

    private void apply(Path path, Method method, Object handler) {

        final LinkedList<String> nodes = new LinkedList<>(Arrays.asList(path.value().split("/")));
        final String leafNode = nodes.removeLast();

        Tree<Node> trunk = buildTrunk(nodes);

        Class<?> parameterType = method.getParameterTypes()[0];
        if (StartElement.class.isAssignableFrom(parameterType)) {

            Node node = new Node(leafNode, NodeType.START_ELEMENT);

            Tree<Node> t = trunk.getTree(node);
            if (t == null) {

                node.add(new MethodInvoker(method, handler));

                trunk.addLeaf(node);
            } else {
                t.getHead().add(new MethodInvoker(method, handler));
            }

        } else if (EndElement.class.isAssignableFrom(parameterType)) {

            Node node = new Node(leafNode, NodeType.END_ELEMENT);

            Tree<Node> t = trunk.getTree(node);
            if (t == null) {

                node.add(new MethodInvoker(method, handler));

                trunk.addLeaf(node);
            } else {
                t.getHead().add(new MethodInvoker(method, handler));
            }

        } else {
            System.err.println(parameterType);
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

    public void parse(XMLEventReader reader) {

        Tree<Node> parseTree = tree;

        try {
            while (reader.hasNext()) {

                XMLEvent event = reader.nextEvent();

                switch (event.getEventType()) {
                    case XMLStreamConstants.START_ELEMENT:

                        characterStack.push(new StringBuilder());

                        parseTree = invokeStartElementHandlers(parseTree, event.asStartElement());
                        break;

                    case XMLStreamConstants.END_ELEMENT:

                        StringBuilder stringBuilder = characterStack.pop();

                        parseTree = invokeFieldHandlers(parseTree, stringBuilder.toString(), event.asEndElement());
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
        parseTree = parseTree.getTree(new Node(elementName, NodeType.START_ELEMENT));

        parseTree.getHead().invoke(startElement);

        return parseTree;
    }

    private Tree<Node> invokeFieldHandlers(Tree<Node> parseTree, String fieldValue, EndElement endElement) {

        String elementName = endElement.getName().getLocalPart();
        parseTree = parseTree.getTree(new Node(elementName, NodeType.END_ELEMENT));

        parseTree.getHead().invoke(fieldValue);
        parseTree.getHead().invoke(endElement);

        return parseTree;
    }

}
