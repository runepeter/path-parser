package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class FilterAttrAptTest {

    public static class FilterHandler {
        @Path("/menu/food[@id='FRUIT']") public String fruit;
        @Path("/menu/food[@id='BREAD']") public String bread;
    }

    @Test
    void filterMatcherRiktigElement() {
        FilterHandler h = new FilterHandler();
        String xml = "<menu><food id='FRUIT'>Apple</food><food id='BREAD'>Loaf</food></menu>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.fruit).isEqualTo("Apple");
        assertThat(h.bread).isEqualTo("Loaf");
    }
}
