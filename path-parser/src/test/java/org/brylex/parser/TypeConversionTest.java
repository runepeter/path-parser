package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeConversionTest {

    enum Status { ACTIVE, INACTIVE }

    public static class Order {
        @Path("/order/quantity") int quantity;
        @Path("/order/price") BigDecimal price;
        @Path("/order/active") boolean active;
        @Path("/order/created") LocalDate created;
        @Path("/order/uuid") UUID uuid;
        @Path("/order/status") Status status;
    }

    @Test
    void convertsAllSupportedScalarTypes() throws Exception {
        String xml = """
                <order>
                  <quantity>42</quantity>
                  <price>199.95</price>
                  <active>true</active>
                  <created>2024-01-15</created>
                  <uuid>550e8400-e29b-41d4-a716-446655440000</uuid>
                  <status>ACTIVE</status>
                </order>""";

        Order order = parse(xml, new Order());

        assertThat(order.quantity).isEqualTo(42);
        assertThat(order.price).isEqualByComparingTo("199.95");
        assertThat(order.active).isTrue();
        assertThat(order.created).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(order.uuid).isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(order.status).isEqualTo(Status.ACTIVE);
    }

    public static class Defaults {
        @Path("/order/missing") int missing;
        @Path("/order/quantity") int quantity;
    }

    @Test
    void leavesPrimitiveFieldsAtDefaultWhenElementMissing() throws Exception {
        String xml = "<order><quantity>5</quantity></order>";

        Defaults defaults = parse(xml, new Defaults());

        assertThat(defaults.quantity).isEqualTo(5);
        assertThat(defaults.missing).isEqualTo(0);
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {
            
            new PathParser(handler).parse(reader);
        }
        return handler;
    }
}
