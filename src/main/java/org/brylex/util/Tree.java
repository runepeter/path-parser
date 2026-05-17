package org.brylex.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tree<T> {

    private final T head;
    private final List<Tree<T>> leafs = new ArrayList<>();
    private Map<T, Tree<T>> locate = new HashMap<>();

    public Tree(T head) {
        this.head = head;
        locate.put(head, this);
    }

    public Tree<T> addLeaf(T leaf) {
        Tree<T> t = new Tree<>(leaf);
        leafs.add(t);
        t.locate = this.locate;
        locate.put(leaf, t);
        return t;
    }

    public T getHead() {
        return head;
    }

    public Tree<T> getTree(T element) {
        return locate.get(element);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        appendTo(builder, 0);
        return builder.toString();
    }

    private void appendTo(StringBuilder builder, int indent) {
        builder.append(" ".repeat(indent)).append(head);
        for (Tree<T> child : leafs) {
            builder.append('\n');
            child.appendTo(builder, indent + 2);
        }
    }
}
