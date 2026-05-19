package org.brylex.parser;

import java.util.function.Function;

public interface PathParserFactory {

    Class<?> handlerType();

    ParseNode tree();

    InvokerSet bind(Object handler,
                    Function<Class<?>, Object> subHandlerFactory,
                    Function<Class<?>, PathParserFactory> subFactoryLookup);
}
