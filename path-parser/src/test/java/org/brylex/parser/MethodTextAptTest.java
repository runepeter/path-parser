package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class MethodTextAptTest {

    public static class MethHandler {
        public int last;
        @Path("/r/qty") public void onQty(int v) { this.last = v; }
    }

    @Test
    void metodeKallesMedKonvertertVerdi() {
        MethHandler h = new MethHandler();
        PathParser.of(h).parse(new StringReader("<r><qty>7</qty></r>"));
        assertThat(h.last).isEqualTo(7);
    }
}
