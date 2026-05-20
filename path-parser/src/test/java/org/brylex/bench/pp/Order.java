package org.brylex.bench.pp;

import org.brylex.parser.annotation.Path;

import java.math.BigDecimal;
import java.util.List;

public class Order {

    @Path("/id") public String id;
    @Path("/customer") public String customer;
    @Path("/total") public BigDecimal total;
    @Path("/items/item") public List<Item> items;
}
