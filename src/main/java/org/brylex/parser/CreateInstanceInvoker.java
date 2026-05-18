package org.brylex.parser;

import javax.xml.stream.XMLStreamReader;
import java.util.function.Function;

public final class CreateInstanceInvoker implements Invoker {

    private final Class<?> type;
    private final Function<Class<?>, Object> factory;

    Object value;

    public CreateInstanceInvoker(Class<?> type, Function<Class<?>, Object> factory) {
        this.type = type;
        this.factory = factory;
    }

    @Override
    public void invoke(Object argument) {
        Object handler = factory.apply(type);
        PathParser parser = new PathParser(new ParseNode("/", null, null), handler, factory);
        parser.parse((XMLStreamReader) argument);
        this.value = handler;
    }
}
