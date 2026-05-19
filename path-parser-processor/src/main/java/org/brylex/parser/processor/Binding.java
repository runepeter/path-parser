package org.brylex.parser.processor;

import javax.lang.model.element.Element;
import java.util.List;

public sealed interface Binding {

    String path();

    Element element();

    record FieldText(String path, Element element, String fieldName, String fieldType) implements Binding {
    }

    record MethodText(String path, Element element, String methodName, String paramType) implements Binding {
    }

    record MethodEvent(String path, Element element, String methodName, EventKind eventKind) implements Binding {
        public enum EventKind { START, END }
    }

    record Attribute(String path, Element element, String fieldName, String fieldType, String attrName) implements Binding {
    }

    record Collection(String path, Element element, String fieldName,
                      String collectionType, String elementType) implements Binding {
    }

    record SubHandler(String path, Element element, String targetName, String subHandlerType,
                      Kind kind, String collectionType) implements Binding {
        public enum Kind { FIELD, METHOD, COLLECTION_FIELD }
    }

    static List<Binding> ofNone() {
        return List.of();
    }
}
