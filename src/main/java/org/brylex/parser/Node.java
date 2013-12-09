package org.brylex.parser;

import java.util.HashSet;
import java.util.Set;

public class Node {

    private final String name;
    private final NodeType type;
    private final Set<Invoker> invokers;

    Node(String name, NodeType type) {
        this.name = name;
        this.type = type;
        this.invokers = new HashSet<>();
    }

    public void add(Invoker invoker) {
        this.invokers.add(invoker);
    }

    public void invoke(Object argument) {
        for (Invoker invoker : invokers) {
            invoker.invoke(argument);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;

        Node node = (Node) o;

        if (!name.equals(node.name)) return false;
        if (type != node.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Node(" + name + ", " + type + ", " + invokers + ")";
    }
}
