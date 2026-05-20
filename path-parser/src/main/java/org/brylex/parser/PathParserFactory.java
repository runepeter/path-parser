package org.brylex.parser;

import java.util.function.Function;

public interface PathParserFactory {

    Class<?> handlerType();

    ParseNode tree();

    InvokerSet bind(Object handler,
                    Function<Class<?>, Object> subHandlerFactory,
                    Function<Class<?>, PathParserFactory> subFactoryLookup);

    /**
     * Creates a fresh (unshared) parse tree and binds the given handler to it.
     * Use this for sub-handler instances that are created per-parse invocation
     * to avoid accumulating invokers on the shared static TREE.
     *
     * Default implementation falls back to using the shared tree (for backward compat).
     */
    default ParseNode bindFresh(Object handler,
                                Function<Class<?>, Object> subHandlerFactory,
                                Function<Class<?>, PathParserFactory> subFactoryLookup) {
        bind(handler, subHandlerFactory, subFactoryLookup);
        return tree();
    }
}
