package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class AttributeAptTest {

    public static class AttrHandler {
        @Path("/order/@id") public String orderId;
        @Path("/order/customer/@type") public String customerType;
        @Path("/order/@total") public int total;
    }

    @Test
    void attributtMappingFungerer() {
        AttrHandler h = new AttrHandler();
        String xml = "<order id='X1' total='42'><customer type='PRO'>Acme</customer></order>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.orderId).isEqualTo("X1");
        assertThat(h.customerType).isEqualTo("PRO");
        assertThat(h.total).isEqualTo(42);
    }
}
