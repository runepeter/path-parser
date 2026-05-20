package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EdgeCasesTest {

    public static class Holder {
        @Path("/x/value") String value;
    }

    @Test
    void emptyElementYieldsEmptyString() throws Exception {
        Holder holder = parse("<x><value/></x>", new Holder());

        assertThat(holder.value).isEqualTo("");
    }

    @Test
    void whitespaceOnlyTextPreserved() throws Exception {
        Holder holder = parse("<x><value>   </value></x>", new Holder());

        assertThat(holder.value).isEqualTo("   ");
    }

    @Test
    void commentsAndProcessingInstructionsAreIgnored() throws Exception {
        String xml = """
                <?xml version='1.0'?>
                <x><!-- a comment --><value>ok<?app instruction?></value></x>""";

        Holder holder = parse(xml, new Holder());

        assertThat(holder.value).isEqualTo("ok");
    }

    @Test
    void namespacedElementsMatchByLocalName() throws Exception {
        String xml = "<x:x xmlns:x='http://example.com'><x:value>ns-ok</x:value></x:x>";

        Holder holder = parse(xml, new Holder());

        assertThat(holder.value).isEqualTo("ns-ok");
    }

    @Test
    void unregisteredSiblingsDoNotBreakParsing() throws Exception {
        String xml = "<x><other>X</other><value>Y</value><deeper><nested>Z</nested></deeper></x>";

        Holder holder = parse(xml, new Holder());

        assertThat(holder.value).isEqualTo("Y");
    }

    @Test
    void malformedXmlThrows() {
        assertThatThrownBy(() -> parse("<x><value>broken</x>", new Holder()))
                .isInstanceOf(RuntimeException.class);
    }

    public static class MultiAttribute {
        @Path("/order/@id") String id;
        @Path("/order/@status") String status;
        @Path("/order/@total") int total;
    }

    @Test
    void readsMultipleAttributesInAnyOrder() throws Exception {
        MultiAttribute h = parse("<order status='OPEN' total='99' id='X-1'/>", new MultiAttribute());

        assertThat(h.id).isEqualTo("X-1");
        assertThat(h.status).isEqualTo("OPEN");
        assertThat(h.total).isEqualTo(99);
    }

    public static class TextWithAttribute {
        @Path("/x/item/@id") String itemId;
        @Path("/x/item") String itemText;
    }

    @Test
    void elementWithAttributesAlsoCapturesText() throws Exception {
        TextWithAttribute h = parse("<x><item id='42'>hello</item></x>", new TextWithAttribute());

        assertThat(h.itemId).isEqualTo("42");
        assertThat(h.itemText).isEqualTo("hello");
    }

    private static <T> T parse(String xml, T handler) throws Exception {
        try (Reader reader = new StringReader(xml)) {
            
            new PathParser(handler).parse(reader);
        }
        return handler;
    }
}
