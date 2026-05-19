package org.brylex.bench.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;

import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
public class Item {

    @XmlElement public String sku;
    @XmlElement public int qty;
    @XmlElement public BigDecimal price;
}
