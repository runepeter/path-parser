package org.brylex.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Intern parse-trenode. Erstatter Tree&lt;Node&gt;+Node-duoen som krevde
 * objekt-allokering per element-oppslag.
 *
 * Hver node har bade start- og end-invokere (kalt ved hhv. START_ELEMENT og
 * END_ELEMENT for elementer som matcher denne noden). Barn er indeksert pa
 * lokalt navn for allokeringsfri oppslag, med en sekundaer mekanisme for
 * attributt-filtrerte noder ({@code food[@id='FRUIT']}-syntaks).
 */
public final class ParseNode {

    final String name;
    final String filterAttrName;
    final String filterAttrValue;

    public final List<Invoker> startInvokers = new ArrayList<>(0);
    public final List<Invoker> endInvokers = new ArrayList<>(0);

    public boolean needsText;
    public boolean needsStartElement;
    public boolean needsEndElement;

    private Map<String, ChildBucket> children;

    public ParseNode(String name, String filterAttrName, String filterAttrValue) {
        this.name = name;
        this.filterAttrName = filterAttrName;
        this.filterAttrValue = filterAttrValue;
    }

    public ParseNode addChild(String name, String filterAttrName, String filterAttrValue) {
        if (children == null) {
            children = new HashMap<>(4);
        }
        ChildBucket bucket = children.computeIfAbsent(name, k -> new ChildBucket());
        for (ParseNode existing : bucket.entries) {
            if (eq(existing.filterAttrName, filterAttrName) && eq(existing.filterAttrValue, filterAttrValue)) {
                return existing;
            }
        }
        ParseNode created = new ParseNode(name, filterAttrName, filterAttrValue);
        bucket.entries.add(created);
        if (filterAttrName == null) {
            bucket.filterless = created;
        } else {
            bucket.hasFiltered = true;
        }
        return created;
    }

    public ParseNode lookupChild(String name, int attrCount, String[] attrNames, String[] attrValues) {
        if (children == null) {
            return null;
        }
        ChildBucket bucket = children.get(name);
        if (bucket == null) {
            return null;
        }
        if (bucket.hasFiltered && attrCount > 0) {
            List<ParseNode> entries = bucket.entries;
            for (int e = 0, sz = entries.size(); e < sz; e++) {
                ParseNode candidate = entries.get(e);
                String fan = candidate.filterAttrName;
                if (fan == null) continue;
                String fav = candidate.filterAttrValue;
                for (int i = 0; i < attrCount; i++) {
                    if (fan.equals(attrNames[i]) && fav.equals(attrValues[i])) {
                        return candidate;
                    }
                }
            }
        }
        return bucket.filterless;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static final class ChildBucket {
        final List<ParseNode> entries = new ArrayList<>(2);
        ParseNode filterless;
        boolean hasFiltered;
    }
}
