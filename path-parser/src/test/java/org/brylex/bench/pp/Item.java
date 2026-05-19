package org.brylex.bench.pp;

import org.brylex.parser.annotation.Path;

import java.math.BigDecimal;

public class Item {

    @Path("/sku") public String sku;
    @Path("/qty") public int qty;
    @Path("/price") public BigDecimal price;
}
