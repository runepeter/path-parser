package org.brylex.parser;

public sealed interface Invoker permits FieldInvoker, MethodInvoker, CreateInstanceInvoker, ApplySubParserInvoker {
    void invoke(Object argument);
}
