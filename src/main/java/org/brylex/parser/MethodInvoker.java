package org.brylex.parser;

import java.lang.reflect.Method;

/**
* Created by runepeter on 09.12.13.
*/
public class MethodInvoker implements Invoker {

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
        if (!(o instanceof MethodInvoker)) return false;

        MethodInvoker that = (MethodInvoker) o;

        if (!handler.equals(that.handler)) return false;
        if (!method.equals(that.method)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + handler.hashCode();
        return result;
    }
}
