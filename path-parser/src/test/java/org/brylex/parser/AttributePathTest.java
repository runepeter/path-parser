package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributePathTest {

    public static class OrderAttrs {
        @Path("/order/@id") String id;
        @Path("/order/@total") int total;
    }

    @Test
    void readsMultipleAttributesFromSameElement() throws Exception {
        String xml = "<order id='ORD-1' total='42'/>";

        OrderAttrs order = parse(xml, new OrderAttrs());

        assertThat(order.id).isEqualTo("ORD-1");
        assertThat(order.total).isEqualTo(42);
    }

    public static class NestedAttr {
        @Path("/order/customer/@type") String customerType;
        @Path("/order/customer") String customer;
    }

    @Test
    void readsAttributeOnChildElementAndStillCapturesText() throws Exception {
        String xml = "<order><customer type='PREMIUM'>Alice</customer></order>";

        NestedAttr handler = parse(xml, new NestedAttr());

        assertThat(handler.customerType).isEqualTo("PREMIUM");
        assertThat(handler.customer).isEqualTo("Alice");
    }

    public static class MissingAttr {
        @Path("/order/@id") String id;
        @Path("/order/@missing") String missing;
    }

    @Test
    void leavesFieldNullWhenAttributeMissing() throws Exception {
        String xml = "<order id='X'/>";

        MissingAttr handler = parse(xml, new MissingAttr());

        assertThat(handler.id).isEqualTo("X");
        assertThat(handler.missing).isNull();
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {

            PathParser.of(handler).parse(reader);
        }
        return handler;
    }
}
