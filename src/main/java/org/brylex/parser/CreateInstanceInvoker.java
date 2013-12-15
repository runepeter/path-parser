package org.brylex.parser;

import org.brylex.util.Tree;

import javax.xml.stream.XMLEventReader;

public class CreateInstanceInvoker implements Invoker {

    final Class<?> type;

    Object value;

    public CreateInstanceInvoker(Class<?> type) {
        this.type = type;
    }

    @Override
    public void invoke(Object argument) {

        try {
            Object handler = type.newInstance(); // TODO rpbjo: support for different factories.

            PathParser parser = new PathParser(new Tree<>(new Node("/", NodeType.START_ELEMENT)), handler);
            parser.parse((XMLEventReader) argument);

            this.value = handler;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
