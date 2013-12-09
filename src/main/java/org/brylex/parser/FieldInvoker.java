package org.brylex.parser;

import java.lang.reflect.Field;

/**
 * Created by runepeter on 09.12.13.
 */
public class FieldInvoker implements Invoker {

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
            field.set(handler, argument);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to apply value [" + argument + "] to handler [" + handler + "].", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldInvoker)) return false;

        FieldInvoker that = (FieldInvoker) o;

        if (!field.equals(that.field)) return false;
        if (!handler.equals(that.handler)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = field.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }
}
