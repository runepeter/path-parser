package org.brylex.parser;

import javax.xml.stream.XMLStreamConstants;

public enum NodeType {
    START_ELEMENT(XMLStreamConstants.START_ELEMENT),
    START_DOCUMENT(XMLStreamConstants.START_DOCUMENT),
    END_ELEMENT(XMLStreamConstants.END_ELEMENT);

    private final int type;

    NodeType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
