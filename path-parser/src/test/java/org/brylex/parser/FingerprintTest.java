package org.brylex.parser;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintTest {

    @Test
    void allRegistriesHaveFingerprint() {
        boolean foundAny = false;
        for (PathParserFactoryRegistry r : ServiceLoader.load(PathParserFactoryRegistry.class)) {
            foundAny = true;
            assertThat(r.fingerprint()).isNotBlank();
        }
        assertThat(foundAny).as("test-modulen må ha registries fra APT").isTrue();
    }
}
