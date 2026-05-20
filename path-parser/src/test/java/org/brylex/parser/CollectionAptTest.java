package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionAptTest {

    public static class CollHandler {
        @Path("/doc/items/item") public List<String> items;
        @Path("/doc/prices/p") public List<BigDecimal> prices;
        @Path("/doc/tags/tag") public Set<String> tags;
    }

    @Test
    void collectsRepeatedElements() {
        CollHandler h = new CollHandler();
        String xml = "<doc>"
                + "<items><item>A</item><item>B</item></items>"
                + "<prices><p>1.50</p><p>2.50</p></prices>"
                + "<tags><tag>x</tag><tag>y</tag></tags>"
                + "</doc>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.items).containsExactly("A", "B");
        assertThat(h.prices).containsExactly(new BigDecimal("1.50"), new BigDecimal("2.50"));
        assertThat(h.tags).containsExactlyInAnyOrder("x", "y");
    }
}
