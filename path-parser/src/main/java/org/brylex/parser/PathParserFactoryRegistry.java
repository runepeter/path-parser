package org.brylex.parser;

import java.util.Map;

public interface PathParserFactoryRegistry {

    Map<Class<?>, PathParserFactory> factories();

    String fingerprint();
}
