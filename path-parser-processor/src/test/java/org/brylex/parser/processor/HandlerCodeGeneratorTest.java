package org.brylex.parser.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HandlerCodeGeneratorTest {

    @Test
    void genererTreeOgBindForStringField() throws Exception {
        Compilation comp = Compiler.javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceLines("test.SimpleHandler",
                        "package test;",
                        "import org.brylex.parser.annotation.Path;",
                        "public class SimpleHandler {",
                        "  @Path(\"/root/child\") public String child;",
                        "}"));

        Optional<JavaFileObject> generated = comp.generatedSourceFile("test.SimpleHandler_PathParser");
        assertThat(generated).isPresent();

        String actual = generated.get().getCharContent(true).toString();
        String expected = Files.readString(Paths.get("src/test/resources/golden/SimpleHandler_PathParser.golden.java"));

        assertThat(actual.trim()).isEqualTo(expected.trim());
    }
}
