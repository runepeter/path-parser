package org.brylex.bench;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.brylex.parser.PathParser;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EquivalenceTest {

    @Test
    void pathParserAndJaxbProduceEquivalentResults() throws Exception {
        String xml = XmlFixture.orders(10, 3);
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);

        org.brylex.bench.pp.Orders pp = new org.brylex.bench.pp.Orders();
        XMLEventReader reader = XMLInputFactory.newInstance().createXMLEventReader(new ByteArrayInputStream(bytes));
        new PathParser(pp).parse(reader);
        reader.close();

        Unmarshaller unmarshaller = JAXBContext.newInstance(org.brylex.bench.jaxb.Orders.class).createUnmarshaller();
        org.brylex.bench.jaxb.Orders jaxb = (org.brylex.bench.jaxb.Orders) unmarshaller.unmarshal(new ByteArrayInputStream(bytes));

        assertThat(pp.orders).hasSize(10);
        assertThat(jaxb.orders).hasSize(10);

        for (int i = 0; i < 10; i++) {
            org.brylex.bench.pp.Order ppOrder = pp.orders.get(i);
            org.brylex.bench.jaxb.Order jaxbOrder = jaxb.orders.get(i);

            assertThat(ppOrder.id).isEqualTo(jaxbOrder.id);
            assertThat(ppOrder.customer).isEqualTo(jaxbOrder.customer);
            assertThat(ppOrder.total).isEqualByComparingTo(jaxbOrder.total);
            assertThat(ppOrder.items).hasSize(3);

            for (int j = 0; j < 3; j++) {
                assertThat(ppOrder.items.get(j).sku).isEqualTo(jaxbOrder.items.get(j).sku);
                assertThat(ppOrder.items.get(j).qty).isEqualTo(jaxbOrder.items.get(j).qty);
                assertThat(ppOrder.items.get(j).price).isEqualByComparingTo(jaxbOrder.items.get(j).price);
            }
        }
    }
}
