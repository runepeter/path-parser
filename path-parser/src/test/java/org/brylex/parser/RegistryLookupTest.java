package org.brylex.parser;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistryLookupTest {

    @Test
    void serviceLoaderFinnrAPTGenerertRegistry() {
        long count = ServiceLoader.load(PathParserFactoryRegistry.class, getClass().getClassLoader())
                .stream().count();
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
