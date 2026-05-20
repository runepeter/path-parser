package org.brylex.parser;

import java.util.function.Consumer;
import java.util.function.Function;

public final class SubParseActivator implements Invoker {

    private final Class<?> subType;
    private final Function<Class<?>, Object> instanceFactory;
    private final Function<Class<?>, PathParserFactory> factoryLookup;
    private final Consumer<Object> applyToParent;

    public SubParseActivator(Class<?> subType,
                             Function<Class<?>, Object> instanceFactory,
                             Function<Class<?>, PathParserFactory> factoryLookup,
                             Consumer<Object> applyToParent) {
        this.subType = subType;
        this.instanceFactory = instanceFactory;
        this.factoryLookup = factoryLookup;
        this.applyToParent = applyToParent;
    }

    public Class<?> subType() { return subType; }
    public Function<Class<?>, Object> instanceFactory() { return instanceFactory; }
    public Function<Class<?>, PathParserFactory> factoryLookup() { return factoryLookup; }
    public Consumer<Object> applyToParent() { return applyToParent; }

    @Override
    public void invoke(Object argument) {
        // Aktivering håndteres av PathParser.parseLoop via instanceof-sjekk.
    }
}
