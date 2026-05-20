package org.brylex.parser.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorRegistrationTest {

    @Test
    void pathProcessorErRegistrertSomService() {
        assertThat(ProcessorRegistrationTest.class.getClassLoader()
                .getResource("META-INF/services/javax.annotation.processing.Processor"))
                .as("service-deklarasjon må finnes")
                .isNotNull();
    }
}
