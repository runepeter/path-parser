package org.brylex.parser;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryLookupTest {

    @Test
    void serviceLoaderFinnsTomMengdeUtenRegistries() {
        long count = ServiceLoader.load(PathParserFactoryRegistry.class, getClass().getClassLoader())
                .stream().count();
        assertThat(count).isZero();
    }
}
