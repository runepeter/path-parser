package org.brylex.parser;

import javax.xml.namespace.QName;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import java.lang.reflect.Field;

public final class AttributeInvoker implements Invoker {

    private final String attributeName;
    private final Field field;
    private final Object handler;
    private final Class<?> fieldType;

    public AttributeInvoker(String attributeName, Field field, Object handler) {
        this.attributeName = attributeName;
        this.field = field;
        this.handler = handler;
        this.fieldType = field.getType();
    }

    @Override
    public void invoke(Object argument) {
        if (!(argument instanceof StartElement startElement)) {
            return;
        }
        Attribute attribute = startElement.getAttributeByName(new QName(attributeName));
        if (attribute == null) {
            return;
        }
        Object value = Conversions.convert(attribute.getValue(), fieldType);
        if (value == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(handler, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to apply attribute [" + attributeName + "] value [" + attribute.getValue() + "] to handler [" + handler + "].", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributeInvoker that)) return false;
        return attributeName.equals(that.attributeName) && field.equals(that.field) && handler.equals(that.handler);
    }

    @Override
    public int hashCode() {
        int result = attributeName.hashCode();
        result = 31 * result + field.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }
}
