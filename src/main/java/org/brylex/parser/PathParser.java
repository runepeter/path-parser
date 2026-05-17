package org.brylex.parser;

import org.brylex.parser.annotation.Path;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PathParser {

    private static final Pattern FILTER_PATTERN = Pattern.compile("(.+)\\[@(.+)=['\"]?([^'\"\\]]+)['\"]?\\]");
    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final String[] EMPTY_STRINGS = new String[0];

    private final ParseNode root;
    private final Function<Class<?>, Object> factory;

    public PathParser(Object handler) {
        this(handler, PathParser::defaultFactory);
    }

    public PathParser(Object handler, Function<Class<?>, Object> factory) {
        this(new ParseNode("/", null, null), handler, factory);
    }

    PathParser(ParseNode root, Object handler, Function<Class<?>, Object> factory) {
        this.root = root;
        this.factory = factory;

        for (Method method : handler.getClass().getDeclaredMethods()) {
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
        String[] segments = splitPath(path.value());
        int leafIndex = segments.length - 1;
        String leaf = segments[leafIndex];

        if (leaf.startsWith("@")) {
            ParseNode trunk = buildTrunk(segments, leafIndex);
            trunk.startInvokers.add(new AttributeInvoker(leaf.substring(1), field, handler));
            return;
        }

        ParseNode trunk = buildTrunk(segments, leafIndex);

        Class<?> elementType = FieldInvoker.elementTypeOf(field);
        if (elementType != null && !Conversions.canConvert(elementType)) {
            ParseNode leafNode = addChild(trunk, leaf);
            CreateInstanceInvoker creator = new CreateInstanceInvoker(elementType, factory);
            leafNode.startInvokers.add(creator);
            leafNode.endInvokers.add(new ApplySubParserInvoker(new FieldInvoker(field, handler), creator));
            return;
        }

        ParseNode leafNode = addChild(trunk, leaf);
        leafNode.endInvokers.add(new FieldInvoker(field, handler));
        leafNode.needsText = true;
    }

    private void apply(Path path, Method method, Object handler) {
        String[] segments = splitPath(path.value());
        int leafIndex = segments.length - 1;
        String leaf = segments[leafIndex];
        ParseNode trunk = buildTrunk(segments, leafIndex);

        Class<?> parameterType = method.getParameterTypes()[0];

        if (StartElement.class.isAssignableFrom(parameterType)) {
            ParseNode leafNode = addChild(trunk, leaf);
            leafNode.startInvokers.add(new MethodInvoker(method, handler));
            leafNode.needsStartElement = true;
        } else if (EndElement.class.isAssignableFrom(parameterType)) {
            ParseNode leafNode = addChild(trunk, leaf);
            leafNode.endInvokers.add(new MethodInvoker(method, handler));
            leafNode.needsEndElement = true;
        } else if (Conversions.canConvert(parameterType)) {
            ParseNode leafNode = addChild(trunk, leaf);
            leafNode.endInvokers.add(new MethodInvoker(method, handler));
            leafNode.needsText = true;
        } else {
            ParseNode leafNode = addChild(trunk, leaf);
            CreateInstanceInvoker creator = new CreateInstanceInvoker(parameterType, factory);
            leafNode.startInvokers.add(creator);
            leafNode.endInvokers.add(new ApplySubParserInvoker(new MethodInvoker(method, handler), creator));
        }
    }

    private static String[] splitPath(String value) {
        return value.split("/");
    }

    private ParseNode buildTrunk(String[] segments, int endIndex) {
        ParseNode current = root;
        for (int i = 0; i < endIndex; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            current = addChild(current, segment);
        }
        return current;
    }

    private static ParseNode addChild(ParseNode parent, String segment) {
        int bracket = segment.indexOf('[');
        if (bracket < 0) {
            return parent.addChild(segment, null, null);
        }
        Matcher matcher = FILTER_PATTERN.matcher(segment);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed path segment [" + segment + "].");
        }
        return parent.addChild(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    public void parse(InputStream input) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parse(reader);
            reader.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    public void parse(Reader input) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parse(reader);
            reader.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    public void parse(XMLStreamReader reader) {
        try {
            parseLoop(reader);
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    private void parseLoop(XMLStreamReader reader) throws XMLStreamException {
        ParseNode parseTree = root;

        int stackCap = 16;
        ParseNode[] parseTreeStack = new ParseNode[stackCap];
        StringBuilder[] charStack = new StringBuilder[stackCap];
        String[] nameStack = new String[stackCap];

        int depth = 0;
        int ignore = 0;
        int attrBufSize = 8;
        String[] attrNames = new String[attrBufSize];
        String[] attrValues = new String[attrBufSize];

        while (reader.hasNext()) {
            int type = reader.next();
            if (type == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                int attrCount = reader.getAttributeCount();
                if (attrCount > attrBufSize) {
                    attrBufSize = attrCount;
                    attrNames = new String[attrBufSize];
                    attrValues = new String[attrBufSize];
                }
                for (int i = 0; i < attrCount; i++) {
                    attrNames[i] = reader.getAttributeLocalName(i);
                    attrValues[i] = reader.getAttributeValue(i);
                }

                if (ignore > 0) {
                    ignore++;
                    if (depth >= stackCap) {
                        stackCap = grow(stackCap);
                        parseTreeStack = grow(parseTreeStack, stackCap);
                        charStack = grow(charStack, stackCap);
                        nameStack = grow(nameStack, stackCap);
                    }
                    nameStack[depth] = name;
                    depth++;
                    continue;
                }

                ParseNode child = parseTree.lookupChild(name, attrCount, attrNames, attrValues);
                if (child == null) {
                    ignore++;
                    if (depth >= stackCap) {
                        stackCap = grow(stackCap);
                        parseTreeStack = grow(parseTreeStack, stackCap);
                        charStack = grow(charStack, stackCap);
                        nameStack = grow(nameStack, stackCap);
                    }
                    nameStack[depth] = name;
                    depth++;
                    continue;
                }

                boolean usedSubParser = invokeStartHandlers(child, reader, attrCount, attrNames, attrValues);

                if (usedSubParser) {
                    invokeEndHandlers(child, null);
                    continue;
                }

                if (depth >= stackCap) {
                    stackCap = grow(stackCap);
                    parseTreeStack = grow(parseTreeStack, stackCap);
                    charStack = grow(charStack, stackCap);
                    nameStack = grow(nameStack, stackCap);
                }
                parseTreeStack[depth] = parseTree;
                charStack[depth] = null;
                nameStack[depth] = name;
                parseTree = child;
                depth++;

            } else if (type == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth < 0) {
                    return;
                }
                if (ignore > 0) {
                    ignore--;
                    continue;
                }
                invokeEndHandlers(parseTree, charStack[depth]);
                charStack[depth] = null;
                parseTree = parseTreeStack[depth];

            } else if (type == XMLStreamConstants.CHARACTERS || type == XMLStreamConstants.CDATA || type == XMLStreamConstants.SPACE) {
                if (depth > 0 && ignore == 0 && parseTree.needsText) {
                    int idx = depth - 1;
                    StringBuilder sb = charStack[idx];
                    if (sb == null) {
                        sb = new StringBuilder();
                        charStack[idx] = sb;
                    }
                    sb.append(reader.getTextCharacters(), reader.getTextStart(), reader.getTextLength());
                }
            }
        }
    }

    private boolean invokeStartHandlers(ParseNode node, XMLStreamReader reader,
                                        int attrCount, String[] attrNames, String[] attrValues) {
        if (node.startInvokers.isEmpty()) {
            return false;
        }
        boolean usedSubParser = false;
        StartElement startElement = null;
        AttributeSnapshot snapshot = null;

        for (int i = 0, n = node.startInvokers.size(); i < n; i++) {
            Invoker invoker = node.startInvokers.get(i);
            if (invoker instanceof CreateInstanceInvoker creator) {
                creator.invoke(reader);
                usedSubParser = true;
            } else if (invoker instanceof AttributeInvoker attr) {
                if (snapshot == null) {
                    snapshot = new AttributeSnapshot(attrCount, attrNames, attrValues);
                }
                attr.invoke(snapshot);
            } else if (invoker instanceof MethodInvoker method) {
                if (node.needsStartElement) {
                    if (startElement == null) {
                        startElement = buildStartElement(reader, attrCount, attrNames, attrValues);
                    }
                    method.invoke(startElement);
                }
            }
        }
        return usedSubParser;
    }

    private void invokeEndHandlers(ParseNode node, StringBuilder text) {
        if (node.endInvokers.isEmpty()) {
            return;
        }
        String textValue = null;
        EndElement endElement = null;

        for (int i = 0, n = node.endInvokers.size(); i < n; i++) {
            Invoker invoker = node.endInvokers.get(i);
            if (invoker instanceof ApplySubParserInvoker apply) {
                apply.fire();
            } else if (invoker instanceof MethodInvoker method && node.needsEndElement && EndElement.class.isAssignableFrom(method.argumentType())) {
                if (endElement == null) {
                    endElement = buildEndElement(node.name);
                }
                method.invoke(endElement);
            } else {
                if (textValue == null) {
                    textValue = text == null ? "" : text.toString();
                }
                invoker.invoke(textValue);
            }
        }
    }

    private static StartElement buildStartElement(XMLStreamReader reader, int attrCount, String[] attrNames, String[] attrValues) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        java.util.List<javax.xml.stream.events.Attribute> attrs = new java.util.ArrayList<>(attrCount);
        for (int i = 0; i < attrCount; i++) {
            attrs.add(factory.createAttribute(new QName(attrNames[i]), attrValues[i]));
        }
        return factory.createStartElement(new QName(reader.getNamespaceURI(), reader.getLocalName()), attrs.iterator(), java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static EndElement buildEndElement(String name) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        return factory.createEndElement(new QName(name), java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static int grow(int cap) {
        return cap * 2;
    }

    private static <T> T[] grow(T[] arr, int newCap) {
        T[] copy = java.util.Arrays.copyOf(arr, newCap);
        return copy;
    }

    private static String[] grow(String[] arr, int newCap) {
        return java.util.Arrays.copyOf(arr, newCap);
    }

    private static Object defaultFactory(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to instantiate [" + type + "].", e);
        }
    }
}
