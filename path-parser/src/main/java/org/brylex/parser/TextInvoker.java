package org.brylex.parser;

import java.util.function.Consumer;

/**
 * Generert tekst-binding. Aktiveres ved END_ELEMENT med element-tekst som argument.
 */
public final class TextInvoker implements Invoker {
    private final Consumer<String> action;
    public TextInvoker(Consumer<String> action) { this.action = action; }
    @Override public void invoke(Object argument) { action.accept((String) argument); }
}
