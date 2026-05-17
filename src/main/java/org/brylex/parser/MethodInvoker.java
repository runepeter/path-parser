package org.brylex.parser;

import java.lang.reflect.Method;

public final class MethodInvoker implements Invoker {

    private final Method method;
    private final Object handler;
    private final Class<?> argumentType;

    public MethodInvoker(Method method, Object handler) {
        this.method = method;
        this.handler = handler;
        this.argumentType = method.getParameterTypes()[0];
    }

    @Override
    public void invoke(Object argument) {

        if (argument == null) {
            throw new IllegalArgumentException("Cannot invoke with [null] argument.");
        }

        if (!argumentType.isAssignableFrom(argument.getClass())) {
            return;
        }

        try {
            method.invoke(handler, argument);
        } catch (Exception e) {
            throw new RuntimeException("Unable to invoke method [" + method + "] on handler [" + handler + "] using argument value [" + argument + "].", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodInvoker that)) return false;
        return method.equals(that.method) && handler.equals(that.handler);
    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }
}
