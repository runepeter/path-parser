package example;

import org.brylex.parser.annotation.Path;

import java.math.BigDecimal;

public class OrderHandler {
    @Path("/order/id") public String id;
    @Path("/order/total") public BigDecimal total;
}
