package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SubHandlerCollectionTest {

    public static class Item {
        @Path("/sku") String sku;
        @Path("/qty") int qty;
        @Path("/price") BigDecimal price;
    }

    public static class Order {
        @Path("/order/item") List<Item> items;
    }

    @Test
    void collectsRepeatedSubHandlerInstances() throws Exception {
        String xml = """
                <order>\
                <item><sku>A-1</sku><qty>2</qty><price>9.99</price></item>\
                <item><sku>B-2</sku><qty>1</qty><price>19.50</price></item>\
                <item><sku>C-3</sku><qty>5</qty><price>0.25</price></item>\
                </order>""";

        Order order = parse(xml, new Order());

        assertThat(order.items).hasSize(3);
        assertThat(order.items.get(0).sku).isEqualTo("A-1");
        assertThat(order.items.get(0).qty).isEqualTo(2);
        assertThat(order.items.get(0).price).isEqualByComparingTo("9.99");
        assertThat(order.items.get(1).sku).isEqualTo("B-2");
        assertThat(order.items.get(2).sku).isEqualTo("C-3");
        assertThat(order.items.get(2).qty).isEqualTo(5);
    }

    public static class PreInitOrder {
        @Path("/order/item") final List<Item> items = new ArrayList<>();
    }

    @Test
    void appendsToPreInitialisedSubHandlerList() throws Exception {
        String xml = "<order><item><sku>X</sku></item><item><sku>Y</sku></item></order>";

        PreInitOrder order = parse(xml, new PreInitOrder());

        assertThat(order.items).hasSize(2);
        assertThat(order.items).extracting(i -> i.sku).containsExactly("X", "Y");
    }

    @Test
    void noElementsLeavesFieldNull() throws Exception {
        Order order = parse("<order/>", new Order());

        assertThat(order.items).isNull();
    }

    public static class NestedRoot {
        @Path("/root/orders/order") List<Order2> orders;

        public static class Order2 {
            @Path("/id") String id;
            @Path("/total") BigDecimal total;
        }
    }

    @Test
    void supportsDeepPathsToSubHandlerList() throws Exception {
        String xml = """
                <root>
                  <orders>
                    <order><id>O-1</id><total>100.00</total></order>
                    <order><id>O-2</id><total>250.50</total></order>
                  </orders>
                </root>""";

        NestedRoot root = parse(xml, new NestedRoot());

        assertThat(root.orders).hasSize(2);
        assertThat(root.orders.get(0).id).isEqualTo("O-1");
        assertThat(root.orders.get(0).total).isEqualByComparingTo("100.00");
        assertThat(root.orders.get(1).id).isEqualTo("O-2");
        assertThat(root.orders.get(1).total).isEqualByComparingTo("250.50");
    }

    public static class WithCustomFactory {
        @Path("/items/item") List<Tracked> items;
    }

    public static class Tracked {
        @Path("/name") String name;
        final String created;

        public Tracked() {
            this.created = "default";
        }

        Tracked(String created) {
            this.created = created;
        }
    }

    @Test
    void subHandlerListUsesCustomFactory() throws Exception {
        String xml = "<items><item><name>A</name></item><item><name>B</name></item></items>";

        WithCustomFactory handler = new WithCustomFactory();
        try (Reader reader = new StringReader(xml)) {

            PathParser.of(handler, type -> new Tracked("custom")).parse(reader);
        }

        assertThat(handler.items).hasSize(2);
        assertThat(handler.items).allMatch(t -> "custom".equals(t.created));
        assertThat(handler.items).extracting(t -> t.name).containsExactly("A", "B");
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {

            PathParser.of(handler).parse(reader);
        }
        return handler;
    }
}
