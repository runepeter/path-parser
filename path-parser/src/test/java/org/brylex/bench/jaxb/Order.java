package org.brylex.bench.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;

import java.math.BigDecimal;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class Order {

    @XmlElement public String id;
    @XmlElement public String customer;
    @XmlElement public BigDecimal total;

    @XmlElementWrapper(name = "items")
    @XmlElement(name = "item")
    public List<Item> items;
}
