package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeConversionAptTest {

    public static class TypedHandler {
        @Path("/o/qty") public int quantity;
        @Path("/o/price") public BigDecimal price;
        @Path("/o/date") public LocalDate date;
        @Path("/o/id") public UUID id;
        @Path("/o/status") public Status status;
    }

    public enum Status { OPEN, CLOSED }

    @Test
    void aptKonverterer() {
        TypedHandler h = new TypedHandler();
        String xml = "<o><qty>42</qty><price>9.99</price><date>2026-05-19</date>"
                + "<id>00000000-0000-0000-0000-000000000001</id><status>OPEN</status></o>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.quantity).isEqualTo(42);
        assertThat(h.price).isEqualByComparingTo("9.99");
        assertThat(h.date).isEqualTo(LocalDate.of(2026, 5, 19));
        assertThat(h.id).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(h.status).isEqualTo(Status.OPEN);
    }
}
