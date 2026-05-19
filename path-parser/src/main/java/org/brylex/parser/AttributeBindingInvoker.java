package org.brylex.parser;

import java.util.function.BiConsumer;

/**
 * Generert attributt-binding. Aktiveres ved START_ELEMENT med AttributeSnapshot.
 */
public final class AttributeBindingInvoker implements Invoker {
    private final String attrName;
    private final BiConsumer<AttributeSnapshot, String> action;

    public AttributeBindingInvoker(String attrName, BiConsumer<AttributeSnapshot, String> action) {
        this.attrName = attrName;
        this.action = action;
    }

    public String attrName() { return attrName; }

    @Override
    public void invoke(Object argument) {
        AttributeSnapshot snap = (AttributeSnapshot) argument;
        String value = snap.attributeValue(attrName);
        if (value != null) action.accept(snap, value);
    }
}
