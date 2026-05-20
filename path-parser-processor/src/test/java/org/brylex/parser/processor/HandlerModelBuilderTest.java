package org.brylex.parser.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

class HandlerModelBuilderTest {

    @Test
    void modellAvSimpelStringHandler() {
        ModelCapturingProcessor capture = new ModelCapturingProcessor();
        Compilation comp = Compiler.javac()
                .withProcessors(capture)
                .compile(JavaFileObjects.forSourceLines("test.SimpleHandler",
                        "package test;",
                        "import org.brylex.parser.annotation.Path;",
                        "public class SimpleHandler {",
                        "  @Path(\"/root/child\") public String child;",
                        "}"));
        assertThat(comp).succeeded();
        org.assertj.core.api.Assertions.assertThat(capture.last).isNotNull();
        org.assertj.core.api.Assertions.assertThat(capture.last.bindings()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(capture.last.bindings().get(0))
                .isInstanceOf(Binding.FieldText.class);
    }
}
