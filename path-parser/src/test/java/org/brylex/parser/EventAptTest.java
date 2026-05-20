package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class EventAptTest {

    public static class EvtHandler {
        public int starts;
        public int ends;

        @Path("/r/x") public void onStart(StartElement e) { starts++; }
        @Path("/r/x") public void onEnd(EndElement e) { ends++; }
    }

    @Test
    void starsOgEndsKalles() {
        EvtHandler h = new EvtHandler();
        PathParser.of(h).parse(new StringReader("<r><x/><x>data</x><x/></r>"));
        assertThat(h.starts).isEqualTo(3);
        assertThat(h.ends).isEqualTo(3);
    }
}
