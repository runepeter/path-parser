package org.brylex.parser;

/**
 * Lett, mutbar projeksjon av attributtene på et XML-start-element. Brukes for
 * å unngå per-element-allokering når AttributeBindingInvoker leser
 * attributtverdier.
 *
 * Holdes ikke på tvers av invoke-kall; ny snapshot per START-element der det
 * finnes attributt-baserte invokere.
 */
public final class AttributeSnapshot {

    final int count;
    final String[] names;
    final String[] values;

    AttributeSnapshot(int count, String[] names, String[] values) {
        this.count = count;
        this.names = names;
        this.values = values;
    }

    String value(String name) {
        for (int i = 0; i < count; i++) {
            if (names[i].equals(name)) {
                return values[i];
            }
        }
        return null;
    }

    public String attributeValue(String name) {
        return value(name);
    }
}
