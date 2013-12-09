package org.brylex;


import org.brylex.parser.annotation.Path;
import org.brylex.util.Tree;
import org.junit.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppTest {

    @Test
    public void testApp() throws Exception {

        String xml = "<xml>" +
                "<child>A</child>" +
                "<child>B</child>" +
                "<child>C</child>" +
                "</xml>";

        PathParser parser = new PathParser(new TestParserHandler());

        try (Reader reader = new StringReader(xml)) {

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);

            parser.parse(xmlEventReader);
        }
    }

    public static class PathParser {

        private final Tree<Node> tree;

        public PathParser(Object... handlers) {

            this.tree = new Tree<Node>(new Node("/", NodeType.START_DOCUMENT));

            for (Object handler : handlers) {

                Method[] methods = handler.getClass().getDeclaredMethods();
                for (Method method : methods) {

                    Path path = method.getAnnotation(Path.class);
                    if (path != null) {
                        apply(path, method, handler);
                    }
                }
            }
        }

        private void apply(Path path, Method method, Object handler) {

            String[] split = path.value().split("/");

            Tree<Node> parent = tree;
            for (int i = 0; i < split.length - 1; i++) {

                String step = split[i];

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

            String step = split[split.length - 1];

            Class<?> parameterType = method.getParameterTypes()[0];
            if (StartElement.class.isAssignableFrom(parameterType)) {

                Node node = new Node(step, NodeType.START_ELEMENT);

                Tree<Node> t = parent.getTree(node);
                if (t == null) {

                    node.add(method, handler);

                    parent.addLeaf(node);
                } else {
                    t.getHead().add(method, handler);
                }

            } else if (EndElement.class.isAssignableFrom(parameterType)) {

                Node node = new Node(step, NodeType.END_ELEMENT);

                Tree<Node> t = parent.getTree(node);
                if (t == null) {

                    node.add(method, handler);

                    parent.addLeaf(node);
                } else {
                    t.getHead().add(method, handler);
                }

            } else {
                System.err.println(parameterType);
            }
        }

        private enum NodeType {
            START_ELEMENT(XMLStreamConstants.START_ELEMENT),
            START_DOCUMENT(XMLStreamConstants.START_DOCUMENT),
            END_ELEMENT(XMLStreamConstants.END_ELEMENT);

            private final int type;

            NodeType(int type) {
                this.type = type;
            }

            public int getType() {
                return type;
            }
        }

        private class Node {

            private final String name;
            private final NodeType type;
            private final Map<Method, Object> methods;

            private Node(String name, NodeType type) {
                this.name = name;
                this.type = type;
                this.methods = new HashMap<>();
            }

            public void add(Method method, Object instance) {
                this.methods.put(method, instance);
            }

            public void invoke(Method method, Object argument) {
                try {
                    method.invoke(methods.get(method), argument);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            public Set<Method> handlers() {
                return new HashSet<>(methods.keySet());
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Node)) return false;

                Node node = (Node) o;

                if (!name.equals(node.name)) return false;
                if (type != node.type) return false;

                return true;
            }

            @Override
            public int hashCode() {
                int result = name.hashCode();
                result = 31 * result + type.hashCode();
                return result;
            }

            @Override
            public String toString() {
                return "Node(" + name + ", " + type + ", " + methods + ")";
            }
        }

        public void parse(XMLEventReader reader) {

            Tree<Node> parseTree = tree;

            try {
                while (reader.hasNext()) {

                    XMLEvent event = reader.nextEvent();

                    switch (event.getEventType()) {
                        case XMLStreamConstants.START_ELEMENT:

                            parseTree = invokeStartElementHandlers(parseTree, event.asStartElement());
                            break;

                        case XMLStreamConstants.END_ELEMENT:

                            parseTree = invokeEndElementHandlers(parseTree, event.asEndElement());
                            break;

                        case XMLStreamConstants.START_DOCUMENT:

                            parseTree = tree.getTree(new Node("/", NodeType.START_DOCUMENT));

                            break;
                        case XMLStreamConstants.END_DOCUMENT:
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

            Node node = parseTree.getHead();
            for (Method method : node.handlers()) {
                node.invoke(method, startElement);
            }

            return parseTree;
        }

        private Tree<Node> invokeEndElementHandlers(Tree<Node> parseTree, EndElement endElement) {

            String elementName = endElement.getName().getLocalPart();
            parseTree = parseTree.getTree(new Node(elementName, NodeType.END_ELEMENT));

            Node node = parseTree.getHead();
            for (Method method : node.handlers()) {
                node.invoke(method, endElement);
            }

            return parseTree;
        }

    }

    private static class TestParserHandler {

        @Path("/xml")
        public void handleXml(StartElement element) {
            System.err.println("handleXml.START(" + element + ")");
        }

        @Path("/xml")
        public void handleXml(EndElement element) {
            System.err.println("handleXml.END(" + element + ")");
        }

        @Path("/xml/child")
        public void handleChild(StartElement element) {
            System.err.println("handleChild.START(" + element + ")");
        }

        @Path("/xml/child")
        public void handleChild(EndElement element) {
            System.err.println("handleChild.END(" + element + ")");
        }
    }
}
