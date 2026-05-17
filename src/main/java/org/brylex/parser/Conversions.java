package org.brylex.parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Konverterer tekst (typisk XML-elementinnhold eller attributtverdi) til
 * felt-/parametertypen. Returnerer null hvis ingen konvertering passer.
 */
final class Conversions {

    private Conversions() {
    }

    static Object convert(String text, Class<?> targetType) {
        if (text == null) {
            return null;
        }
        if (targetType == String.class || targetType == CharSequence.class || targetType == Object.class) {
            return text;
        }
        String trimmed = text.trim();
        if (targetType == int.class || targetType == Integer.class) return Integer.valueOf(trimmed);
        if (targetType == long.class || targetType == Long.class) return Long.valueOf(trimmed);
        if (targetType == short.class || targetType == Short.class) return Short.valueOf(trimmed);
        if (targetType == byte.class || targetType == Byte.class) return Byte.valueOf(trimmed);
        if (targetType == double.class || targetType == Double.class) return Double.valueOf(trimmed);
        if (targetType == float.class || targetType == Float.class) return Float.valueOf(trimmed);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.valueOf(trimmed);
        if (targetType == char.class || targetType == Character.class) {
            if (text.length() != 1) {
                throw new IllegalArgumentException("Cannot convert [" + text + "] to char.");
            }
            return text.charAt(0);
        }
        if (targetType == BigInteger.class) return new BigInteger(trimmed);
        if (targetType == BigDecimal.class) return new BigDecimal(trimmed);
        if (targetType == LocalDate.class) return LocalDate.parse(trimmed);
        if (targetType == LocalDateTime.class) return LocalDateTime.parse(trimmed);
        if (targetType == Instant.class) return Instant.parse(trimmed);
        if (targetType == UUID.class) return UUID.fromString(trimmed);
        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object value = Enum.valueOf((Class<? extends Enum>) targetType, trimmed);
            return value;
        }
        return null;
    }
}
