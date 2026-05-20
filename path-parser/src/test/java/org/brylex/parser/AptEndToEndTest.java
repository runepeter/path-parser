package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class AptEndToEndTest {

    public static class SimpleAptHandler {
        @Path("/root/child")
        public String child;
    }

    @Test
    void aptGenerertFactoryOppdagesViaServiceLoader() {
        PathParserFactory factory = GeneratedFactoryRegistry.lookup(SimpleAptHandler.class);
        assertThat(factory).as("APT-bane må produsere factory for handler i samme modul").isNotNull();
    }

    @Test
    void parseBrukerAptIkkeRefleksjon() {
        SimpleAptHandler h = new SimpleAptHandler();
        PathParser.of(h).parse(new StringReader("<root><child>APT</child></root>"));
        assertThat(h.child).isEqualTo("APT");
    }
}
