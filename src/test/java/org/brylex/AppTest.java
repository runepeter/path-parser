package org.brylex;


import org.brylex.parser.PathParser;
import org.brylex.parser.annotation.Path;
import org.junit.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import java.io.Reader;
import java.io.StringReader;

import static org.fest.assertions.Assertions.assertThat;

public class AppTest {

    @Test
    public void testApp() throws Exception {

        String xml = "<xml>" +
                "<child>A</child>" +
                "<child>B</child>" +
                "<sister>X</sister>" +
                "<child>C</child>" +
                "</xml>";

        TestParserHandler handler = new TestParserHandler();

        try (Reader reader = new StringReader(xml)) {

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);

            new PathParser(handler).parse(xmlEventReader);
        }

        assertThat(handler.child).isEqualTo("C");
    }

    public static class TestParserHandler {

        @Path("/xml/child")
        public String child;

        @Path("/xml")
        public void handleXml(StartElement element) {
            System.err.println("handle(  " + element + "  )");
        }

        @Path("/xml")
        public void handleXml(EndElement element) {
            System.err.println("handle(  " + element + "  )");
        }

        @Path("/xml/child")
        public void handleChild(StartElement element) {
            System.err.println("handle(  " + element + "  )");
        }

        @Path("/xml/child")
        public void handleChild(EndElement element) {
            System.err.println("handle(  " + element + "  )");
        }
    }

}
