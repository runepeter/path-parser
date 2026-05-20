package org.brylex.parser;

import java.util.Map;

/**
 * Per-parser binding mellom en handler-instans og dens noder.
 * For runtime-cursor: bare {@link #handler()} brukes; tre-nodene bærer Invokers
 * som allerede er bundet til handleren.
 */
public record InvokerSet(Object handler,
                         Map<Class<?>, PathParserFactory> resolvedSubFactories) {
}
