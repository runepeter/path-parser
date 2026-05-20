package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class PrivateFieldAptTest {

    public static class PrivHandler {
        @Path("/r/v") private String value;
        public String getValue() { return value; }
    }

    @Test
    void privatFeltSettesViaVarHandle() {
        PrivHandler h = new PrivHandler();
        PathParser.of(h).parse(new StringReader("<r><v>secret</v></r>"));
        assertThat(h.getValue()).isEqualTo("secret");
    }
}
