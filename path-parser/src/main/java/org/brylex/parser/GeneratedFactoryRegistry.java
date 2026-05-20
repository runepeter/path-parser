package org.brylex.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

final class GeneratedFactoryRegistry {

    private static final ConcurrentHashMap<ClassLoader, Map<Class<?>, PathParserFactory>> CACHE
            = new ConcurrentHashMap<>();

    private GeneratedFactoryRegistry() {}

    static PathParserFactory lookup(Class<?> handlerType) {
        ClassLoader cl = handlerType.getClassLoader();
        if (cl == null) cl = ClassLoader.getSystemClassLoader();
        Map<Class<?>, PathParserFactory> map = CACHE.computeIfAbsent(cl, GeneratedFactoryRegistry::buildMap);
        return map.get(handlerType);
    }

    private static Map<Class<?>, PathParserFactory> buildMap(ClassLoader cl) {
        Map<Class<?>, PathParserFactory> map = new HashMap<>();
        for (PathParserFactoryRegistry registry : ServiceLoader.load(PathParserFactoryRegistry.class, cl)) {
            map.putAll(registry.factories());
        }
        return map;
    }
}
