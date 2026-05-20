package org.brylex.parser;

import java.util.function.Consumer;

/**
 * Generert StartElement/EndElement-event-binding.
 */
public final class EventInvoker implements Invoker {
    public enum Kind { START_ELEMENT, END_ELEMENT }

    private final Kind kind;
    private final Consumer<Object> action;

    public EventInvoker(Kind kind, Consumer<Object> action) {
        this.kind = kind;
        this.action = action;
    }

    public Kind kind() { return kind; }
    @Override public void invoke(Object argument) { action.accept(argument); }
}
