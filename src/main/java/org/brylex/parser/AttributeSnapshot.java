package org.brylex.parser;

/**
 * Lett, mutbar projeksjon av attributtene pa et XML-start-element. Brukes for
 * a unnga per-element-allokering nar AttributeInvoker leser attributtverdier.
 *
 * Holdes ikke pa tvers av invoke-kall; ny snapshot per START-element der det
 * finnes AttributeInvoker-er.
 */
final class AttributeSnapshot {

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
}
