package org.brylex.bench.pp;

import org.brylex.parser.annotation.Path;

import java.util.List;

public class Orders {

    @Path("/orders/order")
    public List<Order> orders;
}
