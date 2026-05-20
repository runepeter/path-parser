package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class PathParserOfApiTest {

    public static class SimpleHandler {
        @Path("/root/child")
        public String child;
    }

    @Test
    void ofReturnsParserThatBindsFields() {
        SimpleHandler handler = new SimpleHandler();
        PathParser.of(handler).parse(new StringReader("<root><child>X</child></root>"));
        assertThat(handler.child).isEqualTo("X");
    }

    @Test
    void ofWithFactoryAcceptsCustomSubFactory() {
        SimpleHandler handler = new SimpleHandler();
        PathParser.of(handler, type -> {
            throw new IllegalStateException("ikke kalt for ren tekst-mapping");
        }).parse(new StringReader("<root><child>Y</child></root>"));
        assertThat(handler.child).isEqualTo("Y");
    }
}
