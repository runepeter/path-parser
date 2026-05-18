package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodParameterConversionTest {

    public static class Capturing {
        final List<Object> calls = new ArrayList<>();

        @Path("/x/text")
        public void onText(String text) {
            calls.add(text);
        }

        @Path("/x/qty")
        public void onQty(int qty) {
            calls.add(qty);
        }

        @Path("/x/price")
        public void onPrice(BigDecimal price) {
            calls.add(price);
        }

        @Path("/x/date")
        public void onDate(LocalDate date) {
            calls.add(date);
        }

        @Path("/x/flag")
        public void onFlag(boolean flag) {
            calls.add(flag);
        }
    }

    @Test
    void textHandlerMethodsReceiveConvertedScalars() throws Exception {
        String xml = """
                <x>\
                <text>hello</text>\
                <qty>42</qty>\
                <price>99.95</price>\
                <date>2024-01-15</date>\
                <flag>true</flag>\
                </x>""";

        Capturing handler = parse(xml, new Capturing());

        assertThat(handler.calls).containsExactly(
                "hello",
                42,
                new BigDecimal("99.95"),
                LocalDate.of(2024, 1, 15),
                true);
    }

    public static class CalledOncePerOccurrence {
        final List<Integer> values = new ArrayList<>();

        @Path("/items/item")
        public void onItem(int value) {
            values.add(value);
        }
    }

    @Test
    void textHandlerMethodFiresOncePerMatchingElement() throws Exception {
        String xml = "<items><item>1</item><item>2</item><item>3</item></items>";

        CalledOncePerOccurrence handler = parse(xml, new CalledOncePerOccurrence());

        assertThat(handler.values).containsExactly(1, 2, 3);
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {
            
            new PathParser(handler).parse(reader);
        }
        return handler;
    }
}
