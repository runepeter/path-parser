package org.brylex.parser;

import org.brylex.util.Tree;

import javax.xml.stream.XMLEventReader;

public final class CreateInstanceInvoker implements Invoker {

    private final Class<?> type;
    private final java.util.function.Function<Class<?>, Object> factory;

    Object value;

    public CreateInstanceInvoker(Class<?> type, java.util.function.Function<Class<?>, Object> factory) {
        this.type = type;
        this.factory = factory;
    }

    @Override
    public void invoke(Object argument) {

        Object handler = factory.apply(type);

        PathParser parser = new PathParser(new Tree<>(new Node("/", NodeType.START_ELEMENT)), handler, factory);
        parser.parse((XMLEventReader) argument);

        this.value = handler;
    }
}
