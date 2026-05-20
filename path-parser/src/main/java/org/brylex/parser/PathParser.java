package org.brylex.parser;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import java.io.InputStream;
import java.io.Reader;
import java.util.function.Function;

public final class PathParser {

    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

    private final ParseNode root;

    private PathParser(ParseNode root) {
        this.root = root;
    }

    public static PathParser of(Object handler) {
        return of(handler, PathParser::defaultFactory);
    }

    public static PathParser of(Object handler, Function<Class<?>, Object> subHandlerFactory) {
        PathParserFactory factory = GeneratedFactoryRegistry.lookup(handler.getClass());
        if (factory == null) {
            throw new IllegalStateException(
                    "Ingen generert parser for " + handler.getClass().getName()
                            + ". Verifiser at path-parser-processor er aktivert i build-en, og at klassen ble rekompilert.");
        }
        ParseNode freshTree = factory.bindFresh(handler, subHandlerFactory, GeneratedFactoryRegistry::lookup);
        return new PathParser(freshTree);
    }

    public void parse(InputStream input) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parseLoop(reader);
            reader.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    public void parse(Reader input) {
        try {
            XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parseLoop(reader);
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

        int stackCap = 8;
        ParseNode[] parseTreeStack = new ParseNode[stackCap];
        StringBuilder[] charStack = new StringBuilder[stackCap];
        SubParseActivator[] activatorStack = new SubParseActivator[stackCap];
        Object[] subInstanceStack = new Object[stackCap];

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

                if (depth >= stackCap) {
                    int newCap = stackCap * 2;
                    parseTreeStack = java.util.Arrays.copyOf(parseTreeStack, newCap);
                    charStack = java.util.Arrays.copyOf(charStack, newCap);
                    activatorStack = java.util.Arrays.copyOf(activatorStack, newCap);
                    subInstanceStack = java.util.Arrays.copyOf(subInstanceStack, newCap);
                    stackCap = newCap;
                }

                if (ignore > 0) {
                    ignore++;
                    depth++;
                    continue;
                }

                ParseNode child = parseTree.lookupChild(name, attrCount, attrNames, attrValues);
                if (child == null) {
                    ignore++;
                    depth++;
                    continue;
                }

                SubParseActivator activator = findActivator(child);
                if (activator != null) {
                    Object subInstance = activator.instanceFactory().apply(activator.subType());
                    PathParserFactory subFactory = activator.factoryLookup().apply(activator.subType());
                    if (subFactory == null) {
                        throw new IllegalStateException("Ingen generert factory for sub-handler-type "
                                + activator.subType().getName());
                    }
                    ParseNode freshSubTree = subFactory.bindFresh(subInstance,
                            activator.instanceFactory(), activator.factoryLookup());
                    parseTreeStack[depth] = parseTree;
                    activatorStack[depth] = activator;
                    subInstanceStack[depth] = subInstance;
                    parseTree = freshSubTree;
                    depth++;
                    continue;
                }

                invokeStartHandlers(child, reader, attrCount, attrNames, attrValues);
                parseTreeStack[depth] = parseTree;
                activatorStack[depth] = null;
                subInstanceStack[depth] = null;
                if (child.needsText) {
                    StringBuilder sb = charStack[depth];
                    if (sb == null) {
                        charStack[depth] = new StringBuilder(32);
                    } else {
                        sb.setLength(0);
                    }
                }
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
                SubParseActivator doneActivator = activatorStack[depth];
                if (doneActivator != null) {
                    doneActivator.applyToParent().accept(subInstanceStack[depth]);
                    activatorStack[depth] = null;
                    subInstanceStack[depth] = null;
                    parseTree = parseTreeStack[depth];
                    continue;
                }
                StringBuilder text = parseTree.needsText ? charStack[depth] : null;
                invokeEndHandlers(parseTree, text);
                parseTree = parseTreeStack[depth];

            } else if (type == XMLStreamConstants.CHARACTERS
                    || type == XMLStreamConstants.CDATA
                    || type == XMLStreamConstants.SPACE) {
                if (depth > 0 && ignore == 0 && parseTree.needsText) {
                    charStack[depth - 1].append(reader.getTextCharacters(),
                            reader.getTextStart(), reader.getTextLength());
                }
            }
        }
    }

    private static SubParseActivator findActivator(ParseNode node) {
        for (int i = 0, n = node.startInvokers.size(); i < n; i++) {
            if (node.startInvokers.get(i) instanceof SubParseActivator a) return a;
        }
        return null;
    }

    private void invokeStartHandlers(ParseNode node, XMLStreamReader reader,
                                     int attrCount, String[] attrNames, String[] attrValues) {
        if (node.startInvokers.isEmpty()) return;
        AttributeSnapshot snapshot = null;
        StartElement startElement = null;
        for (int i = 0, n = node.startInvokers.size(); i < n; i++) {
            Invoker inv = node.startInvokers.get(i);
            if (inv instanceof EventInvoker ev && ev.kind() == EventInvoker.Kind.START_ELEMENT) {
                if (startElement == null) {
                    startElement = buildStartElement(reader, attrCount, attrNames, attrValues);
                }
                inv.invoke(startElement);
                continue;
            }
            if (inv instanceof AttributeBindingInvoker) {
                if (snapshot == null) {
                    snapshot = new AttributeSnapshot(attrCount, attrNames, attrValues);
                }
                inv.invoke(snapshot);
            }
        }
    }

    private void invokeEndHandlers(ParseNode node, StringBuilder text) {
        if (node.endInvokers.isEmpty()) return;
        String textValue = text == null ? "" : text.toString();
        EndElement endElement = null;
        for (int i = 0, n = node.endInvokers.size(); i < n; i++) {
            Invoker inv = node.endInvokers.get(i);
            if (inv instanceof EventInvoker ev && ev.kind() == EventInvoker.Kind.END_ELEMENT) {
                if (endElement == null) endElement = buildEndElement(node.name);
                inv.invoke(endElement);
                continue;
            }
            inv.invoke(textValue);
        }
    }

    private static StartElement buildStartElement(XMLStreamReader reader,
                                                  int attrCount, String[] attrNames, String[] attrValues) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        java.util.List<javax.xml.stream.events.Attribute> attrs = new java.util.ArrayList<>(attrCount);
        for (int i = 0; i < attrCount; i++) {
            attrs.add(factory.createAttribute(new QName(attrNames[i]), attrValues[i]));
        }
        return factory.createStartElement(
                new QName(reader.getNamespaceURI(), reader.getLocalName()),
                attrs.iterator(),
                java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static EndElement buildEndElement(String name) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        return factory.createEndElement(new QName(name),
                java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static Object defaultFactory(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to instantiate [" + type + "].", e);
        }
    }
}
