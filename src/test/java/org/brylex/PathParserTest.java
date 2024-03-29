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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PathParserTest {

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

    @Test
    public void testSameElementNamesAtDifferentLevels() throws Exception {

        String xml = "<xml>" +
                "<child>A</child>" +
                "<child>B</child>" +
                "<sister>X</sister>" +
                "<child>C</child>" +
                "<pet><dog><child>Z</child></dog></pet>" +
                "<child>D</child>" +
                "</xml>";

        TestParserHandler handler = new TestParserHandler();

        try (Reader reader = new StringReader(xml)) {

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);

            new PathParser(handler).parse(xmlEventReader);
        }

        assertThat(handler.child).isEqualTo("D");
    }

    @Test
    public void testSubHandler() throws Exception {

        String xml = "<xml>" +
                "<child><grandchild>A</grandchild></child>" +
                "<child><grandchild>B</grandchild></child>" +
                "<child><uncle>X</uncle></child>" +
                "<child><grandchild>C</grandchild></child>" +
                "<sister>X</sister>" +
                "</xml>";

        TestRootHandler handler = new TestRootHandler();

        try (Reader reader = new StringReader(xml)) {

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);

            new PathParser(handler).parse(xmlEventReader);
        }

        assertThat(handler.children).hasSize(4);
        assertThat(handler.children.get(0).grandchild).isEqualTo("A");
        assertThat(handler.children.get(1).grandchild).isEqualTo("B");
        assertThat(handler.children.get(2).grandchild).isNull();
        assertThat(handler.children.get(2).uncle).isEqualTo("X");
        assertThat(handler.children.get(3).grandchild).isEqualTo("C");
    }

    @Test
    public void matchAnnotation() throws Exception {

        String xml = "<xml>" +
                "<child>A</child>" +
                "<food id='FRUIT'>Apple</food>" +
                "<food id='BREAD'>Pain</food>" +
                "</xml>";

        TestParserHandler handler = new TestParserHandler();

        try (Reader reader = new StringReader(xml)) {

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(reader);

            new PathParser(handler).parse(xmlEventReader);
        }

        assertThat(handler.child).isEqualTo("A");
        assertThat(handler.fruit).isEqualTo("Apple");
        assertThat(handler.bread).isEqualTo("Pain");
    }

    public static class TestParserHandler {

        @Path("/xml/child")
        public String child;

        @Path("/xml/food[@id='FRUIT']")
        public String fruit;

        @Path("/xml/food[@id='BREAD']")
        public String bread;

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

    public static class TestRootHandler {

        public final List<TestSubHandler> children = new ArrayList<>();

        @Path("/xml/child")
        public void handleChild(TestSubHandler handler) {
            children.add(handler);
        }
    }

    public static class TestSubHandler {

        @Path("/grandchild")
        public String grandchild;

        @Path("/uncle")
        public String uncle;

    }
}
