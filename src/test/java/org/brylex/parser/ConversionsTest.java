package org.brylex.parser;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionsTest {

    enum Color { RED, GREEN, BLUE }

    @Test
    void returnsTextUnchangedForStringTargets() {
        assertThat(Conversions.convert("hello", String.class)).isEqualTo("hello");
        assertThat(Conversions.convert("hello", CharSequence.class)).isEqualTo("hello");
        assertThat(Conversions.convert("hello", Object.class)).isEqualTo("hello");
    }

    @Test
    void convertsIntegerTypes() {
        assertThat(Conversions.convert("42", int.class)).isEqualTo(42);
        assertThat(Conversions.convert("42", Integer.class)).isEqualTo(42);
        assertThat(Conversions.convert("9999999999", long.class)).isEqualTo(9999999999L);
        assertThat(Conversions.convert("9999999999", Long.class)).isEqualTo(9999999999L);
        assertThat(Conversions.convert("7", short.class)).isEqualTo((short) 7);
        assertThat(Conversions.convert("7", byte.class)).isEqualTo((byte) 7);
    }

    @Test
    void convertsFloatingPointTypes() {
        assertThat(Conversions.convert("3.14", double.class)).isEqualTo(3.14);
        assertThat(Conversions.convert("3.14", Double.class)).isEqualTo(3.14);
        assertThat(Conversions.convert("1.5", float.class)).isEqualTo(1.5f);
    }

    @Test
    void convertsBooleanCharAndBigNumerics() {
        assertThat(Conversions.convert("true", boolean.class)).isEqualTo(true);
        assertThat(Conversions.convert("false", Boolean.class)).isEqualTo(false);
        assertThat(Conversions.convert("X", char.class)).isEqualTo('X');
        assertThat(Conversions.convert("12345", BigInteger.class)).isEqualTo(new BigInteger("12345"));
        assertThat(Conversions.convert("3.14159", BigDecimal.class)).isEqualTo(new BigDecimal("3.14159"));
    }

    @Test
    void convertsDateTimeAndUuid() {
        assertThat(Conversions.convert("2024-01-15", LocalDate.class)).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(Conversions.convert("2024-01-15T10:30:00", LocalDateTime.class)).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30));
        assertThat(Conversions.convert("2024-01-15T10:30:00Z", Instant.class)).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(Conversions.convert("550e8400-e29b-41d4-a716-446655440000", UUID.class))
                .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void convertsEnum() {
        assertThat(Conversions.convert("RED", Color.class)).isEqualTo(Color.RED);
        assertThat(Conversions.convert("BLUE", Color.class)).isEqualTo(Color.BLUE);
    }

    @Test
    void trimsWhitespaceForNumericAndDateTypes() {
        assertThat(Conversions.convert("  42  ", int.class)).isEqualTo(42);
        assertThat(Conversions.convert("\n2024-01-15\t", LocalDate.class)).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void returnsNullForUnknownTargetType() {
        assertThat(Conversions.convert("hello", java.util.List.class)).isNull();
        assertThat(Conversions.convert("hello", Thread.class)).isNull();
    }

    @Test
    void returnsNullForNullText() {
        assertThat(Conversions.convert(null, int.class)).isNull();
        assertThat(Conversions.convert(null, String.class)).isNull();
    }

    @Test
    void throwsOnInvalidCharConversion() {
        assertThatThrownBy(() -> Conversions.convert("ABC", char.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsOnMalformedNumber() {
        assertThatThrownBy(() -> Conversions.convert("not-a-number", int.class))
                .isInstanceOf(NumberFormatException.class);
    }
}
