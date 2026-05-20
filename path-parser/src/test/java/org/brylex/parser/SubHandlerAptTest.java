package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SubHandlerAptTest {

    public static class Item {
        @Path("/sku") public String sku;
        @Path("/price") public String price;
    }

    public static class OrderH {
        public final List<Item> items = new ArrayList<>();
        @Path("/order/item") public void onItem(Item item) { items.add(item); }
    }

    @Test
    void subHandlerMetode() {
        OrderH h = new OrderH();
        String xml = "<order><item><sku>A1</sku><price>9.99</price></item>"
                + "<item><sku>B2</sku><price>1.00</price></item></order>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.items).hasSize(2);
        assertThat(h.items.get(0).sku).isEqualTo("A1");
        assertThat(h.items.get(1).price).isEqualTo("1.00");
    }
}
