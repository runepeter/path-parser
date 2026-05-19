package org.brylex.bench;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.brylex.parser.PathParser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, warmups = 0)
@State(Scope.Benchmark)
public class PathParserVsJaxbBenchmark {

    @Param({"10", "100", "1000"})
    public int orders;

    @Param({"5"})
    public int itemsPerOrder;

    private byte[] xmlBytes;
    private JAXBContext jaxbContext;

    @Setup
    public void setup() throws Exception {
        String xml = XmlFixture.orders(orders, itemsPerOrder);
        xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        jaxbContext = JAXBContext.newInstance(org.brylex.bench.jaxb.Orders.class);
    }

    @Benchmark
    public org.brylex.bench.pp.Orders pathParser() throws Exception {
        org.brylex.bench.pp.Orders root = new org.brylex.bench.pp.Orders();
        new PathParser(root).parse(new ByteArrayInputStream(xmlBytes));
        return root;
    }

    @Benchmark
    public org.brylex.bench.jaxb.Orders jaxb() throws Exception {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (org.brylex.bench.jaxb.Orders) unmarshaller.unmarshal(new ByteArrayInputStream(xmlBytes));
    }
}
