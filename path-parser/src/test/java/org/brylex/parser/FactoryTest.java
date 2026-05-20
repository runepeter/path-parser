package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryTest {

    public static class Item {
        @Path("/name") String name;
        final String origin;

        public Item() {
            this.origin = "default";
        }

        Item(String origin) {
            this.origin = origin;
        }
    }

    public static class Root {
        public final List<Item> items = new ArrayList<>();

        @Path("/items/item")
        public void onItem(Item item) {
            items.add(item);
        }
    }

    @Test
    void usesDefaultFactoryWhenNoneProvided() throws Exception {
        String xml = "<items><item><name>A</name></item></items>";

        Root root = parse(xml, new Root(), null);

        assertThat(root.items).hasSize(1);
        assertThat(root.items.get(0).name).isEqualTo("A");
        assertThat(root.items.get(0).origin).isEqualTo("default");
    }

    @Test
    void customFactoryInvokedForSubHandlerInstances() throws Exception {
        String xml = "<items><item><name>X</name></item><item><name>Y</name></item></items>";
        Set<Class<?>> requested = new HashSet<>();

        Root root = parse(xml, new Root(), type -> {
            requested.add(type);
            return new Item("custom");
        });

        assertThat(requested).containsExactly(Item.class);
        assertThat(root.items).hasSize(2);
        assertThat(root.items).allMatch(i -> "custom".equals(i.origin));
        assertThat(root.items.get(0).name).isEqualTo("X");
        assertThat(root.items.get(1).name).isEqualTo("Y");
    }

    private static <T> T parse(String xml, T handler, java.util.function.Function<Class<?>, Object> factory) throws Exception {
        try (Reader reader = new StringReader(xml)) {

            PathParser parser = factory == null ? new PathParser(handler) : new PathParser(handler, factory);
            parser.parse(reader);
        }
        return handler;
    }
}
