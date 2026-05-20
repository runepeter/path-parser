package example;

import org.brylex.parser.PathParser;

import java.io.StringReader;
import java.math.BigDecimal;

public class Main {
    public static void main(String[] args) {
        String xml = "<order><id>O-42</id><total>199.95</total></order>";
        OrderHandler h = new OrderHandler();
        PathParser.of(h).parse(new StringReader(xml));
        if (!"O-42".equals(h.id) || new BigDecimal("199.95").compareTo(h.total) != 0) {
            System.err.println("FAIL: id=" + h.id + " total=" + h.total);
            System.exit(1);
        }
        System.out.println("OK: " + h.id + " " + h.total);
    }
}
