package org.brylex.parser;

public final class ApplySubParserInvoker implements Invoker {

    private final Invoker delegate;
    private final CreateInstanceInvoker createInstanceInvoker;

    public ApplySubParserInvoker(Invoker delegate, CreateInstanceInvoker createInstanceInvoker) {
        this.delegate = delegate;
        this.createInstanceInvoker = createInstanceInvoker;
    }

    @Override
    public void invoke(Object argument) {
        fire();
    }

    void fire() {
        delegate.invoke(createInstanceInvoker.value);
    }
}
