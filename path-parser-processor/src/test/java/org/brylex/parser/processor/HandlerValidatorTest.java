package org.brylex.parser.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

class HandlerValidatorTest {

    @Test
    void tomPathFeiler() {
        Compilation comp = Compiler.javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceLines("test.X",
                        "package test;",
                        "import org.brylex.parser.annotation.Path;",
                        "public class X { @Path(\"\") public String a; }"));
        assertThat(comp).hadErrorContaining("@Path-verdien kan ikke være tom");
    }

    @Test
    void duplikatPathFeiler() {
        Compilation comp = Compiler.javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceLines("test.Y",
                        "package test;",
                        "import org.brylex.parser.annotation.Path;",
                        "public class Y {",
                        "  @Path(\"/a/b\") public String a;",
                        "  @Path(\"/a/b\") public String b;",
                        "}"));
        assertThat(comp).hadErrorContaining("deklareres to ganger");
    }

    @Test
    void ukjentJavaTypeFeiler() {
        Compilation comp = Compiler.javac()
                .withProcessors(new PathProcessor())
                .compile(JavaFileObjects.forSourceLines("test.Z",
                        "package test;",
                        "import java.net.URL;",
                        "import org.brylex.parser.annotation.Path;",
                        "public class Z { @Path(\"/a\") public URL u; }"));
        assertThat(comp).hadErrorContaining("kan ikke konverteres fra tekst");
    }
}
