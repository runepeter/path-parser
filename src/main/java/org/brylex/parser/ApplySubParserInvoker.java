package org.brylex.parser;

import javax.xml.stream.events.EndElement;

public final class ApplySubParserInvoker implements Invoker {

    private final Invoker delegate;
    private final CreateInstanceInvoker createInstanceInvoker;

    public ApplySubParserInvoker(Invoker delegate, CreateInstanceInvoker createInstanceInvoker) {
        this.delegate = delegate;
        this.createInstanceInvoker = createInstanceInvoker;
    }

    @Override
    public void invoke(Object argument) {
        if (argument instanceof EndElement) {
            delegate.invoke(createInstanceInvoker.value);
        }
    }
}
