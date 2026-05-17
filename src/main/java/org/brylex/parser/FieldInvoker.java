package org.brylex.parser;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

public final class FieldInvoker implements Invoker {

    private final Field field;
    private final Object handler;
    private final Class<?> fieldType;
    private final boolean collection;
    private final Class<?> elementType;

    public FieldInvoker(Field field, Object handler) {
        this.field = field;
        this.handler = handler;
        this.fieldType = field.getType();
        field.setAccessible(true);

        if (Collection.class.isAssignableFrom(fieldType)) {
            this.collection = true;
            this.elementType = resolveElementType(field);
        } else {
            this.collection = false;
            this.elementType = null;
        }
    }

    private static Class<?> resolveElementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt
                && pt.getActualTypeArguments().length > 0
                && pt.getActualTypeArguments()[0] instanceof Class<?> arg) {
            return arg;
        }
        return String.class;
    }

    static Class<?> elementTypeOf(Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return null;
        }
        return resolveElementType(field);
    }

    @Override
    public void invoke(Object argument) {

        if (argument == null) {
            throw new IllegalArgumentException("Cannot set field value to  [null].");
        }

        if (collection) {
            if (argument instanceof String text) {
                Object element = Conversions.convert(text, elementType);
                if (element != null) {
                    addToCollection(element);
                }
                return;
            }
            if (elementType.isInstance(argument)) {
                addToCollection(argument);
            }
            return;
        }

        if (argument instanceof String text) {
            Object value = Conversions.convert(text, fieldType);
            if (value == null) {
                return;
            }
            set(value);
            return;
        }

        if (!fieldType.isAssignableFrom(argument.getClass())) {
            return;
        }
        set(argument);
    }

    private void set(Object value) {
        try {
            field.set(handler, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to apply value [" + value + "] to handler [" + handler + "].", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void addToCollection(Object element) {
        try {
            Collection<Object> existing = (Collection<Object>) field.get(handler);
            if (existing == null) {
                existing = newCollection();
                field.set(handler, existing);
            }
            existing.add(element);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to add to collection on handler [" + handler + "].", e);
        }
    }

    private Collection<Object> newCollection() {
        if (fieldType.isAssignableFrom(ArrayList.class)) return new ArrayList<>();
        if (fieldType.isAssignableFrom(LinkedHashSet.class)) return new LinkedHashSet<>();
        if (fieldType.isAssignableFrom(ArrayDeque.class)) return new ArrayDeque<>();
        throw new IllegalStateException("Cannot instantiate collection for field type [" + fieldType + "]. Initialise the field manually.");
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
