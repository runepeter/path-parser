package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionPathTest {

    public static class StringList {
        @Path("/items/item") List<String> items;
    }

    @Test
    void collectsRepeatedElementsIntoList() throws Exception {
        String xml = "<items><item>A</item><item>B</item><item>C</item></items>";

        StringList handler = parse(xml, new StringList());

        assertThat(handler.items).containsExactly("A", "B", "C");
    }

    public static class IntegerList {
        @Path("/numbers/n") List<Integer> values;
    }

    @Test
    void convertsCollectionElementType() throws Exception {
        String xml = "<numbers><n>1</n><n>2</n><n>3</n></numbers>";

        IntegerList handler = parse(xml, new IntegerList());

        assertThat(handler.values).containsExactly(1, 2, 3);
    }

    public static class PreInitialised {
        @Path("/items/item") final List<String> items = new ArrayList<>();
    }

    @Test
    void appendsToPreInitialisedCollection() throws Exception {
        String xml = "<items><item>A</item><item>B</item></items>";

        PreInitialised handler = parse(xml, new PreInitialised());

        assertThat(handler.items).containsExactly("A", "B");
    }

    public static class StringSet {
        @Path("/items/item") Set<String> items;
    }

    @Test
    void supportsSetField() throws Exception {
        String xml = "<items><item>A</item><item>B</item><item>A</item></items>";

        StringSet handler = parse(xml, new StringSet());

        assertThat(handler.items).containsExactlyInAnyOrder("A", "B");
        assertThat(handler.items).isInstanceOf(LinkedHashSet.class);
    }

    public static class EmptyMatches {
        @Path("/items/item") List<String> items;
    }

    @Test
    void leavesCollectionNullWhenNoMatches() throws Exception {
        String xml = "<items/>";

        EmptyMatches handler = parse(xml, new EmptyMatches());

        assertThat(handler.items).isNull();
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {

            PathParser.of(handler).parse(reader);
        }
        return handler;
    }
}
