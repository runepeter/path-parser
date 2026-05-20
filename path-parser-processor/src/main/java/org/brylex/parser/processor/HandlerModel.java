package org.brylex.parser.processor;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record HandlerModel(TypeElement handlerType, String packageName, String simpleName, List<Binding> bindings) {

    public String generatedClassName() {
        return simpleName + "_PathParser";
    }
}
