package org.brylex.parser;

import java.lang.reflect.Field;

public final class FieldInvoker implements Invoker {

    private final Field field;
    private final Object handler;
    private final Class<?> fieldType;

    public FieldInvoker(Field field, Object handler) {
        this.field = field;
        this.handler = handler;
        this.fieldType = field.getType();
    }

    @Override
    public void invoke(Object argument) {

        if (argument == null) {
            throw new IllegalArgumentException("Cannot set field value to  [null].");
        }

        if (!fieldType.isAssignableFrom(argument.getClass())) {
            return;
        }

        try {
            field.setAccessible(true);
            field.set(handler, argument);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to apply value [" + argument + "] to handler [" + handler + "].", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldInvoker that)) return false;
        return field.equals(that.field) && handler.equals(that.handler);
    }

    @Override
    public int hashCode() {
        int result = field.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }
}
