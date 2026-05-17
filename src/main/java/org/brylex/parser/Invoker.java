package org.brylex.parser;

public sealed interface Invoker permits FieldInvoker, MethodInvoker, CreateInstanceInvoker, ApplySubParserInvoker, AttributeInvoker {
    void invoke(Object argument);
}
