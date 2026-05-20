package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomFactoryAptTest {

    public static class Tag {
        @Path("/name") public String name;
    }

    public static class Doc {
        public final List<Tag> tags = new ArrayList<>();
        @Path("/doc/tag") public void onTag(Tag t) { tags.add(t); }
    }

    @Test
    void customFactoryBrukesForSubHandlerInstans() {
        List<Class<?>> calls = new ArrayList<>();
        Doc d = new Doc();
        PathParser.of(d, type -> {
            calls.add(type);
            try { return type.getDeclaredConstructor().newInstance(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).parse(new StringReader("<doc><tag><name>a</name></tag><tag><name>b</name></tag></doc>"));

        assertThat(calls).containsExactly(Tag.class, Tag.class);
        assertThat(d.tags).hasSize(2);
        assertThat(d.tags.get(0).name).isEqualTo("a");
        assertThat(d.tags.get(1).name).isEqualTo("b");
    }
}
