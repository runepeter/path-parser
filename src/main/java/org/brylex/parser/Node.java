package org.brylex.parser;

import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node {

    private static final Pattern PATTERN = Pattern.compile("(.+)\\[.*@(.+).*\\]");

    private final String name;
    private final NodeType type;
    private final Set<Invoker> invokers;
    private final String idAttribute;
    private final String idValue;

    public Node(String name, NodeType type) {

        Matcher matcher = PATTERN.matcher(name);

        if (matcher.matches()) {

            this.name = matcher.group(1);

            String[] parts = matcher.group(2).split("=");
            idAttribute = parts[0];
            idValue = parts[1].replaceAll("'", "").replaceAll("\"", "");

        } else {
            this.name = name;
            idAttribute = null;
            idValue = null;
        }

        this.type = type;
        this.invokers = new HashSet<>();
    }

    public Node(XMLEvent event) {

        StartElement element = event.asStartElement();

        this.name = element.getName().getLocalPart();

        Iterator attributes = element.getAttributes();
        if (attributes.hasNext()) {
            Attribute attribute = (Attribute) attributes.next();
            this.idAttribute = attribute.getName().getLocalPart();
            this.idValue = attribute.getValue();
        } else {
            this.idAttribute = null;
            this.idValue = null;
        }

        this.type = NodeType.END_ELEMENT;
        this.invokers = new HashSet<>();
    }

    public String getName() {
        return name;
    }

    public NodeType getType() {
        return type;
    }

    public void add(Invoker invoker) {
        this.invokers.add(invoker);
    }

    public void invoke(Object argument) {
        for (Invoker invoker : invokers) {
            invoker.invoke(argument);
        }
    }

    public Set<Invoker> getInvokers() {
        return invokers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;
        return Objects.equals(name, node.name) &&
                type == node.type &&
                Objects.equals(idAttribute, node.idAttribute) &&
                Objects.equals(idValue, node.idValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, idAttribute, idValue);
    }

    @Override
    public String toString() {
        return "Node(" + name + ", " + type + ", " + invokers + ")";
    }
}
