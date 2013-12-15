package org.brylex.parser;

import javax.xml.stream.events.EndElement;

public class ApplySubParserInvoker implements Invoker {

    private final MethodInvoker methodInvoker;
    private final CreateInstanceInvoker createInstanceInvoker;

    public ApplySubParserInvoker(MethodInvoker methodInvoker, CreateInstanceInvoker createInstanceInvoker) {
        this.methodInvoker = methodInvoker;
        this.createInstanceInvoker = createInstanceInvoker;
    }

    @Override
    public void invoke(Object argument) {
        if (argument instanceof EndElement) {
            methodInvoker.invoke(createInstanceInvoker.value);
        }
    }
}
