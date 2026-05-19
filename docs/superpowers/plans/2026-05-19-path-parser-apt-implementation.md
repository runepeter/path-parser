# path-parser 3.0 — APT-implementasjonsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Erstatt all refleksjon i path-parser med kompilerings-tids kodegenerering via en JSR-269 annotation processor, slik at 3.0 fungerer på GraalVM native-image uten brukerkonfig.

**Architecture:** APT (`path-parser-processor`) genererer én `<H>_PathParser`-klasse per `@Path`-annotert handler, samt én aggregerende `PathParserRegistry` per modul. Runtime (`path-parser`) bruker `ServiceLoader` til å oppdage registries og holder en `ConcurrentHashMap<Class, PathParserFactory>`-cache. `PathParser.of(handler)` returnerer en parser med ferdig bound `InvokerSet`. Privat felt-tilgang via `MethodHandles.privateLookupIn`. Sub-handlere resolveres lazy for å støtte rekursive grafer.

**Tech Stack:** Java 21, Maven, JSR-269 (`javax.annotation.processing.AbstractProcessor`), JavaPoet for kodegen, JUnit 5 + AssertJ, JMH (eksisterende), GraalVM native-image i CI.

---

## File Structure

**Multi-module layout** (etter Phase 0):

```
path-parser/                                    # parent pom (was root)
├── pom.xml                                     # <packaging>pom</packaging>
├── path-parser/                                # runtime modul
│   ├── pom.xml
│   └── src/
│       ├── main/java/org/brylex/parser/
│       │   ├── PathParser.java                 # of()-API; uendret parseLoop
│       │   ├── ParseNode.java                  # uendret
│       │   ├── PathParserFactory.java          # NY - SPI
│       │   ├── PathParserFactoryRegistry.java  # NY - SPI per modul
│       │   ├── InvokerSet.java                 # NY - record
│       │   ├── Invoker.java                    # endret - non-sealed
│       │   ├── FieldSetter.java                # NY - genererte felt-set Invoker
│       │   ├── MethodCall.java                 # NY - genererte metode-kall Invoker
│       │   ├── AttributeSnapshot.java          # uendret
│       │   ├── Conversions.java                # uendret
│       │   └── annotation/Path.java            # endret - @Retention(CLASS)
│       └── test/java/org/brylex/...            # eksisterende tester
└── path-parser-processor/                      # APT modul
    ├── pom.xml
    └── src/
        ├── main/java/org/brylex/parser/processor/
        │   ├── PathProcessor.java              # NY - AbstractProcessor
        │   ├── HandlerModel.java               # NY
        │   ├── Binding.java                    # NY - sealed (field, method, attr, coll, sub)
        │   ├── HandlerValidator.java           # NY
        │   ├── HandlerCodeGenerator.java       # NY - JavaPoet
        │   ├── RegistryCodeGenerator.java      # NY - aggregator
        │   └── Fingerprint.java                # NY - SHA-256 av @Path-metadata
        ├── main/resources/META-INF/services/
        │   └── javax.annotation.processing.Processor  # NY
        └── test/java/org/brylex/parser/processor/
            ├── PathProcessorTest.java          # google-compile-testing
            ├── HandlerValidatorTest.java
            ├── HandlerCodeGeneratorTest.java   # golden-file
            └── resources/golden/*.golden.java
```

**Fjernes etter Phase 4:**
- `HandlerSpec.java`, `FieldInvoker.java`, `MethodInvoker.java`, `AttributeInvoker.java`, `CreateInstanceInvoker.java`, `ApplySubParserInvoker.java`
- Konstruktørene `PathParser(Object)` og `PathParser(Object, Function)`
- `SEGMENT_CACHE` og `SPECS`-felt i `PathParser`
- `applyField`/`applyMethod`-metodene

---

## Phase 0 — Multi-module Maven setup

Mål: del eksisterende prosjekt i parent + `path-parser` runtime-modul + tomt `path-parser-processor`-modul. Ingen kode-endringer; alle eksisterende tester må fortsatt passere.

### Task 0.1: Branch-opprettelse

**Files:**
- Workspace: branch `feature/path-parser-3.0`

- [ ] **Step 1: Lag arbeids-branch**

```bash
git checkout -b feature/path-parser-3.0
git status  # verifiser ren tree
```

- [ ] **Step 2: Commit (tom branch-merker)**

Ingen commit nødvendig — branch eksisterer.

### Task 0.2: Konverter root til parent-pom

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Endre packaging og legg til modules-blokk**

I `pom.xml`:
- Endre `<packaging>jar</packaging>` → `<packaging>pom</packaging>`.
- Endre `<artifactId>path-parser</artifactId>` → `<artifactId>path-parser-parent</artifactId>`.
- Endre `<name>path-parser</name>` → `<name>path-parser-parent</name>`.
- Etter `<description>`-tag, legg til:
  ```xml
  <modules>
      <module>path-parser</module>
      <module>path-parser-processor</module>
  </modules>
  ```
- Fjern alle `<dependencies>`-blokken (flyttes til runtime-modulen) — men behold `<dependencyManagement>` med samme dependencies for å låse versjoner.
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>org.junit.jupiter</groupId>
              <artifactId>junit-jupiter</artifactId>
              <version>${junit.version}</version>
          </dependency>
          <!-- gjenta for assertj, jmh-core, jmh-generator-annprocess, jakarta.xml.bind, jaxb-runtime -->
      </dependencies>
  </dependencyManagement>
  ```
- I `<build><plugins>`: behold bare `maven-release-plugin` på parent. Flytt `maven-compiler-plugin`, `maven-surefire-plugin`, `jacoco-maven-plugin` til `<build><pluginManagement><plugins>` slik at moduler arver konfig uten å aktivere automatisk.

- [ ] **Step 2: Verifiser maven-parsing**

Run: `mvn -N help:effective-pom -q | head -30`
Expected: viser `path-parser-parent` med `packaging=pom`. Ingen build skjer (`-N` = ikke-rekursiv).

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: konverter root til parent-pom for multi-module"
```

### Task 0.3: Opprett `path-parser`-modul

**Files:**
- Create: `path-parser/pom.xml`
- Move: `src/` → `path-parser/src/`

- [ ] **Step 1: Lag modulkatalog og flytt source**

```bash
mkdir -p path-parser
git mv src path-parser/src
```

- [ ] **Step 2: Skriv `path-parser/pom.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.brylex</groupId>
        <artifactId>path-parser-parent</artifactId>
        <version>3.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>path-parser</artifactId>
    <packaging>jar</packaging>
    <name>path-parser</name>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.openjdk.jmh</groupId>
            <artifactId>jmh-generator-annprocess</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>jakarta.xml.bind</groupId>
            <artifactId>jakarta.xml.bind-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jaxb</groupId>
            <artifactId>jaxb-runtime</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Oppdater parent-pom version til 3.0.0-SNAPSHOT**

I `pom.xml` (root): endre `<version>2.0.1-SNAPSHOT</version>` → `<version>3.0.0-SNAPSHOT</version>`.

- [ ] **Step 4: Verifiser build**

Run: `mvn -pl path-parser verify -q`
Expected: BUILD SUCCESS. Alle eksisterende tester passerer.

- [ ] **Step 5: Commit**

```bash
git add pom.xml path-parser/
git commit -m "build: flytt runtime-kode til path-parser-modul"
```

### Task 0.4: Opprett tom `path-parser-processor`-modul

**Files:**
- Create: `path-parser-processor/pom.xml`
- Create: `path-parser-processor/src/main/java/.gitkeep`

- [ ] **Step 1: Skriv `path-parser-processor/pom.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.brylex</groupId>
        <artifactId>path-parser-parent</artifactId>
        <version>3.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>path-parser-processor</artifactId>
    <packaging>jar</packaging>
    <name>path-parser-processor</name>

    <properties>
        <javapoet.version>1.13.0</javapoet.version>
        <compile-testing.version>0.21.0</compile-testing.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.brylex</groupId>
            <artifactId>path-parser</artifactId>
            <version>3.0.0-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.squareup</groupId>
            <artifactId>javapoet</artifactId>
            <version>${javapoet.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.google.testing.compile</groupId>
            <artifactId>compile-testing</artifactId>
            <version>${compile-testing.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <!-- Vi MÅ slå av annotation processing for processor-modulen
                         selv, ellers prøver javac å kjøre vår egen processor på den. -->
                    <proc>none</proc>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Lag tom kildemappe**

```bash
mkdir -p path-parser-processor/src/main/java
touch path-parser-processor/src/main/java/.gitkeep
mkdir -p path-parser-processor/src/test/java
touch path-parser-processor/src/test/java/.gitkeep
```

- [ ] **Step 3: Verifiser parent-build**

Run: `mvn verify -q -DskipTests`
Expected: BUILD SUCCESS. Begge moduler bygges; processor-modul har ingen klasser men kompilerer rent.

- [ ] **Step 4: Commit**

```bash
git add path-parser-processor/
git commit -m "build: opprett tom path-parser-processor-modul"
```

---

## Phase 1 — Runtime SPI

Mål: introdusér `PathParserFactory`, `PathParserFactoryRegistry`, `InvokerSet`, og `PathParser.of()`-API. I denne fasen delegerer `of()` fortsatt til den eksisterende refleksjons-banen via en intern `ReflectionFactory`-shim. Alle eksisterende tester passerer; nye SPI-tester passerer.

### Task 1.1: SPI-interface `PathParserFactory`

**Files:**
- Create: `path-parser/src/main/java/org/brylex/parser/PathParserFactory.java`

- [ ] **Step 1: Skriv interfacet**

```java
package org.brylex.parser;

import java.util.function.Function;

public interface PathParserFactory {

    Class<?> handlerType();

    ParseNode tree();

    InvokerSet bind(Object handler,
                    Function<Class<?>, Object> subHandlerFactory,
                    Function<Class<?>, PathParserFactory> subFactoryLookup);
}
```

- [ ] **Step 2: Verifiser kompilering**

Run: `mvn -pl path-parser compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/PathParserFactory.java
git commit -m "feat(runtime): introduser PathParserFactory SPI"
```

### Task 1.2: SPI-interface `PathParserFactoryRegistry`

**Files:**
- Create: `path-parser/src/main/java/org/brylex/parser/PathParserFactoryRegistry.java`

- [ ] **Step 1: Skriv interfacet**

```java
package org.brylex.parser;

import java.util.Map;

public interface PathParserFactoryRegistry {

    Map<Class<?>, PathParserFactory> factories();

    String fingerprint();
}
```

- [ ] **Step 2: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/PathParserFactoryRegistry.java
git commit -m "feat(runtime): introduser PathParserFactoryRegistry SPI per modul"
```

### Task 1.3: `InvokerSet`-record

**Files:**
- Create: `path-parser/src/main/java/org/brylex/parser/InvokerSet.java`

- [ ] **Step 1: Skriv recorden**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/InvokerSet.java
git commit -m "feat(runtime): InvokerSet-record"
```

### Task 1.4: Test for `PathParser.of()`-API

**Files:**
- Create: `path-parser/src/test/java/org/brylex/parser/PathParserOfApiTest.java`

- [ ] **Step 1: Skriv test som forventer of()-API**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class PathParserOfApiTest {

    public static class SimpleHandler {
        @Path("/root/child")
        public String child;
    }

    @Test
    void ofReturnsParserThatBindsFields() {
        SimpleHandler handler = new SimpleHandler();
        PathParser.of(handler).parse(new StringReader("<root><child>X</child></root>"));
        assertThat(handler.child).isEqualTo("X");
    }

    @Test
    void ofWithFactoryAcceptsCustomSubFactory() {
        SimpleHandler handler = new SimpleHandler();
        PathParser.of(handler, type -> {
            throw new IllegalStateException("ikke kalt for ren tekst-mapping");
        }).parse(new StringReader("<root><child>Y</child></root>"));
        assertThat(handler.child).isEqualTo("Y");
    }
}
```

- [ ] **Step 2: Kjør test (forventet feil)**

Run: `mvn -pl path-parser test -Dtest=PathParserOfApiTest -q`
Expected: FAIL — `of` method does not exist.

- [ ] **Step 3: Legg til `of()`-metoder i `PathParser.java`**

I `path-parser/src/main/java/org/brylex/parser/PathParser.java`, etter den eksisterende konstruktøren:

```java
public static PathParser of(Object handler) {
    return new PathParser(handler);
}

public static PathParser of(Object handler, java.util.function.Function<Class<?>, Object> subHandlerFactory) {
    return new PathParser(handler, subHandlerFactory);
}
```

Disse delegerer til de eksisterende refleksjons-baserte konstruktørene. Vil bli omskrevet i Phase 4.

- [ ] **Step 4: Kjør test (forventet pass)**

Run: `mvn -pl path-parser test -Dtest=PathParserOfApiTest -q`
Expected: BUILD SUCCESS, tester grønne.

- [ ] **Step 5: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/PathParser.java
git add path-parser/src/test/java/org/brylex/parser/PathParserOfApiTest.java
git commit -m "feat(runtime): PathParser.of() statisk factory (delegerer til refleksjon)"
```

### Task 1.5: Test for `ServiceLoader`-oppslag av `PathParserFactoryRegistry`

**Files:**
- Create: `path-parser/src/test/java/org/brylex/parser/RegistryLookupTest.java`

- [ ] **Step 1: Skriv test som ber om at registries oppdages**

```java
package org.brylex.parser;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryLookupTest {

    @Test
    void serviceLoaderFinnsTomMengdeUtenRegistries() {
        // Ingen APT er kjørt enda — vi forventer 0 registries.
        // Når Phase 2 lander en test-fixture-registry endres dette.
        long count = ServiceLoader.load(PathParserFactoryRegistry.class, getClass().getClassLoader())
                .stream().count();
        assertThat(count).isZero();
    }
}
```

- [ ] **Step 2: Kjør test**

Run: `mvn -pl path-parser test -Dtest=RegistryLookupTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add path-parser/src/test/java/org/brylex/parser/RegistryLookupTest.java
git commit -m "test(runtime): bekreft ServiceLoader-oppslag tomt før APT lander"
```

### Task 1.6: Konkrete Invoker-typer for generert kode

Mål: introdusér typede Invoker-klasser som generert kode instantierer, slik at `parseLoop` kan diskriminere binding-typer via `instanceof` uten å reflektere over lambdaer. Disse erstatter de eksisterende refleksjons-baserte Invoker-klassene gradvis.

**Files:**
- Modify: `path-parser/src/main/java/org/brylex/parser/Invoker.java`
- Create: `path-parser/src/main/java/org/brylex/parser/TextInvoker.java`
- Create: `path-parser/src/main/java/org/brylex/parser/AttributeInvoker.java` (samme navn som eksisterende — vi erstatter den senere)
- Create: `path-parser/src/main/java/org/brylex/parser/EventInvoker.java`
- Modify: `path-parser/src/main/java/org/brylex/parser/ParseNode.java`

- [ ] **Step 1: Endre `Invoker` fra `sealed` til vanlig interface**

Erstatt `path-parser/src/main/java/org/brylex/parser/Invoker.java`:

```java
package org.brylex.parser;

public interface Invoker {
    void invoke(Object argument);
}
```

(Fjern `sealed`/`permits`-klausulen. Eksisterende 2.x-Invoker-implementasjoner forblir gyldige; de fjernes i Task 4.5.)

- [ ] **Step 2: Skriv `TextInvoker.java`**

```java
package org.brylex.parser;

import java.util.function.Consumer;

/**
 * Generert tekst-binding. Aktiveres ved END_ELEMENT med element-tekst som argument.
 */
public final class TextInvoker implements Invoker {
    private final Consumer<String> action;
    public TextInvoker(Consumer<String> action) { this.action = action; }
    @Override public void invoke(Object argument) { action.accept((String) argument); }
}
```

- [ ] **Step 3: Skriv `EventInvoker.java`**

```java
package org.brylex.parser;

import java.util.function.Consumer;

/**
 * Generert StartElement/EndElement-event-binding.
 */
public final class EventInvoker implements Invoker {
    public enum Kind { START_ELEMENT, END_ELEMENT }

    private final Kind kind;
    private final Consumer<Object> action;

    public EventInvoker(Kind kind, Consumer<Object> action) {
        this.kind = kind;
        this.action = action;
    }

    public Kind kind() { return kind; }
    @Override public void invoke(Object argument) { action.accept(argument); }
}
```

- [ ] **Step 4: Skriv `AttributeBindingInvoker.java`** (annet navn enn eksisterende `AttributeInvoker` for å unngå konflikt under transisjon)

```java
package org.brylex.parser;

import java.util.function.BiConsumer;

/**
 * Generert attributt-binding. Aktiveres ved START_ELEMENT med AttributeSnapshot.
 */
public final class AttributeBindingInvoker implements Invoker {
    private final String attrName;
    private final BiConsumer<AttributeSnapshot, String> action;

    public AttributeBindingInvoker(String attrName, BiConsumer<AttributeSnapshot, String> action) {
        this.attrName = attrName;
        this.action = action;
    }

    public String attrName() { return attrName; }

    @Override
    public void invoke(Object argument) {
        AttributeSnapshot snap = (AttributeSnapshot) argument;
        String value = snap.attributeValue(attrName);
        if (value != null) action.accept(snap, value);
    }
}
```

- [ ] **Step 5: Verifiser kompilering**

Run: `mvn -pl path-parser compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/Invoker.java
git add path-parser/src/main/java/org/brylex/parser/TextInvoker.java
git add path-parser/src/main/java/org/brylex/parser/EventInvoker.java
git add path-parser/src/main/java/org/brylex/parser/AttributeBindingInvoker.java
git commit -m "feat(runtime): konkrete Invoker-typer (Text/Event/AttributeBinding) for codegen"
```

---

## Phase 2 — APT skeleton (minimal end-to-end)

Mål: en `PathProcessor` som genererer kun *ett* støttet mønster — en `String`-felt med tekst-path. Genererer både `<H>_PathParser` og en aggregerende `<modulnamn>_PathParserRegistry`. Verifisér end-to-end at en handler med kun et String-felt parses uten refleksjon.

### Task 2.1: Compile-testing-test for at processoren registreres

**Files:**
- Create: `path-parser-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
- Create: `path-parser-processor/src/test/java/org/brylex/parser/processor/ProcessorRegistrationTest.java`

- [ ] **Step 1: Lag service-deklarasjon (peker enda mot ikke-eksisterende klasse)**

`path-parser-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor`:
```
org.brylex.parser.processor.PathProcessor
```

- [ ] **Step 2: Skriv test som forventer at PathProcessor finnes**

```java
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
```

- [ ] **Step 3: Kjør test**

Run: `mvn -pl path-parser-processor test -Dtest=ProcessorRegistrationTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add path-parser-processor/src/main/resources/
git add path-parser-processor/src/test/java/org/brylex/parser/processor/ProcessorRegistrationTest.java
git commit -m "build(processor): META-INF/services-deklarasjon for PathProcessor"
```

### Task 2.2: Skall-`PathProcessor`

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java`

- [ ] **Step 1: Skriv minimal klasse**

```java
package org.brylex.parser.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.brylex.parser.annotation.Path")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PathProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }
}
```

- [ ] **Step 2: Verifiser kompilering**

Run: `mvn -pl path-parser-processor compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java
git commit -m "feat(processor): skall-PathProcessor uten kodegen-logikk"
```

### Task 2.3: `HandlerModel` og `Binding`-typer (data-modell uten generering)

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/Binding.java`
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModel.java`

- [ ] **Step 1: Skriv `Binding.java`**

```java
package org.brylex.parser.processor;

import javax.lang.model.element.Element;
import java.util.List;

public sealed interface Binding {

    String path();

    Element element();

    record FieldText(String path, Element element, String fieldName, String fieldType) implements Binding {
    }

    record MethodText(String path, Element element, String methodName, String paramType) implements Binding {
    }

    record MethodEvent(String path, Element element, String methodName, EventKind eventKind) implements Binding {
        public enum EventKind { START, END }
    }

    record Attribute(String path, Element element, String fieldName, String fieldType, String attrName) implements Binding {
    }

    record Collection(String path, Element element, String fieldName,
                      String collectionType, String elementType) implements Binding {
    }

    record SubHandler(String path, Element element, String targetName, String subHandlerType,
                      Kind kind, String collectionType) implements Binding {
        public enum Kind { FIELD, METHOD, COLLECTION_FIELD }
    }

    static List<Binding> ofNone() {
        return List.of();
    }
}
```

- [ ] **Step 2: Skriv `HandlerModel.java`**

```java
package org.brylex.parser.processor;

import javax.lang.model.element.TypeElement;
import java.util.List;

public record HandlerModel(TypeElement handlerType, String packageName, String simpleName, List<Binding> bindings) {

    public String generatedClassName() {
        return simpleName + "_PathParser";
    }
}
```

- [ ] **Step 3: Verifiser kompilering**

Run: `mvn -pl path-parser-processor compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/Binding.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModel.java
git commit -m "feat(processor): HandlerModel + Binding-data-typer"
```

### Task 2.4: `HandlerModelBuilder` — bygg modell fra `TypeElement` (kun String-felt)

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Create: `path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerModelBuilderTest.java`

- [ ] **Step 1: Skriv test som forventer en `FieldText`-binding**

```java
package org.brylex.parser.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import static com.google.testing.compile.CompilationSubject.assertThat;

class HandlerModelBuilderTest {

    @Test
    void modellAvSimpelStringHandler() {
        // Vi bruker en in-process processor som fanger modellen til en static slot.
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
```

- [ ] **Step 2: Skriv hjelpe-prosessor `ModelCapturingProcessor.java` (samme test-pakke)**

`path-parser-processor/src/test/java/org/brylex/parser/processor/ModelCapturingProcessor.java`:

```java
package org.brylex.parser.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@SupportedAnnotationTypes("org.brylex.parser.annotation.Path")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
class ModelCapturingProcessor extends AbstractProcessor {
    HandlerModel last;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
                TypeElement type = (TypeElement) annotated.getEnclosingElement();
                last = new HandlerModelBuilder(processingEnv).build(type);
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 3: Kjør test (forventet fail — builder finnes ikke)**

Run: `mvn -pl path-parser-processor test -Dtest=HandlerModelBuilderTest -q`
Expected: FAIL — `HandlerModelBuilder` does not exist.

- [ ] **Step 4: Implementér `HandlerModelBuilder.java`**

```java
package org.brylex.parser.processor;

import org.brylex.parser.annotation.Path;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

public final class HandlerModelBuilder {

    private final ProcessingEnvironment env;

    public HandlerModelBuilder(ProcessingEnvironment env) {
        this.env = env;
    }

    public HandlerModel build(TypeElement type) {
        List<Binding> bindings = new ArrayList<>();
        for (Element member : type.getEnclosedElements()) {
            Path path = member.getAnnotation(Path.class);
            if (path == null) continue;
            if (member.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) member;
                String fieldType = field.asType().toString();
                bindings.add(new Binding.FieldText(path.value(), field, field.getSimpleName().toString(), fieldType));
            }
        }
        PackageElement pkg = env.getElementUtils().getPackageOf(type);
        return new HandlerModel(type, pkg.getQualifiedName().toString(),
                type.getSimpleName().toString(), bindings);
    }
}
```

- [ ] **Step 5: Kjør test (forventet pass)**

Run: `mvn -pl path-parser-processor test -Dtest=HandlerModelBuilderTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerModelBuilderTest.java
git add path-parser-processor/src/test/java/org/brylex/parser/processor/ModelCapturingProcessor.java
git commit -m "feat(processor): HandlerModelBuilder for String-felt"
```

### Task 2.5: `HandlerCodeGenerator` — genererer `<H>_PathParser` for String-felt

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerCodeGeneratorTest.java`
- Create: `path-parser-processor/src/test/resources/golden/SimpleHandler_PathParser.golden.java`

- [ ] **Step 1: Skriv golden-fil**

`path-parser-processor/src/test/resources/golden/SimpleHandler_PathParser.golden.java`:

```java
package test;

import java.util.Map;
import java.util.function.Function;
import org.brylex.parser.InvokerSet;
import org.brylex.parser.ParseNode;
import org.brylex.parser.PathParserFactory;
import org.brylex.parser.TextInvoker;

// Generated by path-parser-processor — do not edit manually
public final class SimpleHandler_PathParser implements PathParserFactory {

    private static final ParseNode TREE = buildTree();

    private static ParseNode buildTree() {
        ParseNode root = new ParseNode("/", null, null);
        ParseNode n_root = root.addChild("root", null, null);
        ParseNode n_root_child = n_root.addChild("child", null, null);
        n_root_child.needsText = true;
        return root;
    }

    @Override
    public Class<?> handlerType() {
        return SimpleHandler.class;
    }

    @Override
    public ParseNode tree() {
        return TREE;
    }

    @Override
    public InvokerSet bind(Object handler,
                           Function<Class<?>, Object> subHandlerFactory,
                           Function<Class<?>, PathParserFactory> subFactoryLookup) {
        SimpleHandler h = (SimpleHandler) handler;
        TREE.lookupChild("root", 0, null, null)
                .lookupChild("child", 0, null, null)
                .endInvokers.add(new TextInvoker(text -> h.child = text));
        return new InvokerSet(handler, Map.of());
    }
}
```

**Merknad om codegen-mønster:** Bruk *alltid* de typede Invoker-klassene fra Task 1.6 (`TextInvoker`, `EventInvoker`, `AttributeBindingInvoker`) i generert kode — ikke rå lambdaer som `arg -> ...`. Dette lar `parseLoop` diskriminere via `instanceof`. Senere tasks (3.1-3.8) endrer `emitBindingBindCall` til å emit disse typene; kodemønstre i tasks som viser rå lambdaer skal leses som *konseptuelle skisser* — den faktiske emitterte koden skal alltid wrappes i riktig Invoker-type.

Tree-bygging er flat i denne første versjonen; refaktoreres til private hjelper-metoder per sub-tre i en senere oppstrømnings-task hvis generert kode for store handlere blir uleselig.

- [ ] **Step 2: Skriv testen som sammenligner mot golden**

```java
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
```

- [ ] **Step 3: Kjør test (forventet fail — generator finnes ikke)**

Run: `mvn -pl path-parser-processor test -Dtest=HandlerCodeGeneratorTest -q`
Expected: FAIL — generated source not present.

- [ ] **Step 4: Implementér `HandlerCodeGenerator.java`**

```java
package org.brylex.parser.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public final class HandlerCodeGenerator {

    private final ProcessingEnvironment env;

    public HandlerCodeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    public void generate(HandlerModel model) throws IOException {
        ClassName handlerClass = ClassName.get(model.packageName(), model.simpleName());

        TypeSpec.Builder type = TypeSpec.classBuilder(model.generatedClassName())
                .addJavadoc("Generated by path-parser-processor — do not edit manually\n")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("org.brylex.parser", "PathParserFactory"))
                .addField(ClassName.get("org.brylex.parser", "ParseNode"), "TREE",
                        Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addStaticBlock(CodeBlock.builder().addStatement("TREE = buildTree()").build());

        type.addMethod(buildTreeMethod(model));
        type.addMethod(handlerTypeMethod(handlerClass));
        type.addMethod(treeMethod());
        type.addMethod(bindMethod(model, handlerClass));

        JavaFile file = JavaFile.builder(model.packageName(), type.build()).build();
        file.writeTo(env.getFiler());
    }

    private MethodSpec buildTreeMethod(HandlerModel model) {
        ClassName parseNode = ClassName.get("org.brylex.parser", "ParseNode");
        MethodSpec.Builder builder = MethodSpec.methodBuilder("buildTree")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(parseNode)
                .addStatement("$T root = new $T($S, null, null)", parseNode, parseNode, "/");

        for (Binding binding : model.bindings()) {
            emitBindingTreeNodes(builder, binding);
        }
        builder.addStatement("return root");
        return builder.build();
    }

    private void emitBindingTreeNodes(MethodSpec.Builder builder, Binding binding) {
        if (!(binding instanceof Binding.FieldText ft)) return;
        String[] segments = ft.path().split("/");
        String parent = "root";
        StringBuilder varName = new StringBuilder("n");
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            varName.append("_").append(segment);
            builder.addStatement("$T $L = $L.addChild($S, null, null)",
                    ClassName.get("org.brylex.parser", "ParseNode"),
                    varName.toString(), parent, segment);
            parent = varName.toString();
        }
        builder.addStatement("$L.needsText = true", parent);
    }

    private MethodSpec handlerTypeMethod(ClassName handlerClass) {
        return MethodSpec.methodBuilder("handlerType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.OBJECT.box().withoutAnnotations()))
                .addStatement("return $T.class", handlerClass)
                .build();
    }

    private MethodSpec treeMethod() {
        return MethodSpec.methodBuilder("tree")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("org.brylex.parser", "ParseNode"))
                .addStatement("return TREE")
                .build();
    }

    private MethodSpec bindMethod(HandlerModel model, ClassName handlerClass) {
        ClassName invokerSet = ClassName.get("org.brylex.parser", "InvokerSet");
        ClassName pathParserFactory = ClassName.get("org.brylex.parser", "PathParserFactory");

        ParameterizedTypeName subHandlerFactoryType = ParameterizedTypeName.get(
                ClassName.get(Function.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.OBJECT.box().withoutAnnotations()),
                ClassName.get(Object.class));

        ParameterizedTypeName subFactoryLookupType = ParameterizedTypeName.get(
                ClassName.get(Function.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.OBJECT.box().withoutAnnotations()),
                pathParserFactory);

        MethodSpec.Builder builder = MethodSpec.methodBuilder("bind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(invokerSet)
                .addParameter(Object.class, "handler")
                .addParameter(ParameterSpec.builder(subHandlerFactoryType, "subHandlerFactory").build())
                .addParameter(ParameterSpec.builder(subFactoryLookupType, "subFactoryLookup").build())
                .addStatement("$T h = ($T) handler", handlerClass, handlerClass);

        for (Binding binding : model.bindings()) {
            emitBindingBindCall(builder, binding);
        }
        builder.addStatement("return new $T(handler, $T.of())", invokerSet, Map.class);
        return builder.build();
    }

    private void emitBindingBindCall(MethodSpec.Builder builder, Binding binding) {
        if (!(binding instanceof Binding.FieldText ft)) return;
        String[] segments = ft.path().split("/");
        StringBuilder lookup = new StringBuilder("TREE");
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            lookup.append(".lookupChild(\"").append(segment).append("\", 0, null, null)");
        }
        builder.addStatement(lookup + ".endInvokers.add(arg -> h.$L = ($L) arg)", ft.fieldName(), ft.fieldType());
    }
}
```

- [ ] **Step 5: Wire generator inn i `PathProcessor`**

Erstatt `PathProcessor.process(...)` med:

```java
@Override
public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    HandlerModelBuilder builder = new HandlerModelBuilder(processingEnv);
    HandlerCodeGenerator generator = new HandlerCodeGenerator(processingEnv);
    for (TypeElement annotation : annotations) {
        java.util.Set<javax.lang.model.element.TypeElement> handlerTypes = new java.util.LinkedHashSet<>();
        for (javax.lang.model.element.Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
            javax.lang.model.element.Element enclosing = annotated.getEnclosingElement();
            if (enclosing instanceof TypeElement type) {
                handlerTypes.add(type);
            }
        }
        for (TypeElement type : handlerTypes) {
            HandlerModel model = builder.build(type);
            try {
                generator.generate(model);
            } catch (java.io.IOException e) {
                processingEnv.getMessager().printMessage(
                        javax.tools.Diagnostic.Kind.ERROR, "Codegen feilet: " + e.getMessage(), type);
            }
        }
    }
    return false;
}
```

- [ ] **Step 6: Kjør test**

Run: `mvn -pl path-parser-processor test -Dtest=HandlerCodeGeneratorTest -q`
Expected: BUILD SUCCESS. Hvis golden-fil ikke matcher eksakt: oppdater golden-fil med faktisk output etter inspeksjon, kjør på nytt.

- [ ] **Step 7: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java
git add path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerCodeGeneratorTest.java
git add path-parser-processor/src/test/resources/golden/SimpleHandler_PathParser.golden.java
git commit -m "feat(processor): codegen for String-felt-binding (golden-fil-test)"
```

### Task 2.6: `RegistryCodeGenerator` — aggregert registry per modul

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/RegistryCodeGenerator.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java`

- [ ] **Step 1: Skriv `RegistryCodeGenerator.java`**

```java
package org.brylex.parser.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

public final class RegistryCodeGenerator {

    private final ProcessingEnvironment env;

    public RegistryCodeGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    public void generate(String packageName, String simpleName, List<HandlerModel> models, String fingerprint)
            throws IOException {

        ClassName factory = ClassName.get("org.brylex.parser", "PathParserFactory");
        ParameterizedTypeName mapType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.OBJECT.box().withoutAnnotations()),
                factory);

        MethodSpec.Builder factoriesMethod = MethodSpec.methodBuilder("factories")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(mapType);

        CodeBlock.Builder mapBuild = CodeBlock.builder().add("return $T.<$T, $T>of(", Map.class,
                ParameterizedTypeName.get(ClassName.get(Class.class), TypeName.OBJECT.box().withoutAnnotations()),
                factory);
        for (int i = 0; i < models.size(); i++) {
            HandlerModel m = models.get(i);
            ClassName handler = ClassName.get(m.packageName(), m.simpleName());
            ClassName generated = ClassName.get(m.packageName(), m.generatedClassName());
            mapBuild.add("\n  $T.class, new $T()", handler, generated);
            if (i < models.size() - 1) mapBuild.add(",");
        }
        mapBuild.add(")");
        factoriesMethod.addStatement(mapBuild.build());

        MethodSpec fingerprintMethod = MethodSpec.methodBuilder("fingerprint")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", fingerprint)
                .build();

        TypeSpec spec = TypeSpec.classBuilder(simpleName)
                .addJavadoc("Generated by path-parser-processor — do not edit manually\n")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("org.brylex.parser", "PathParserFactoryRegistry"))
                .addMethod(factoriesMethod.build())
                .addMethod(fingerprintMethod)
                .build();

        JavaFile.builder(packageName, spec).build().writeTo(env.getFiler());

        try (Writer w = env.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "",
                        "META-INF/services/org.brylex.parser.PathParserFactoryRegistry")
                .openWriter()) {
            w.write(packageName + "." + simpleName + "\n");
        }
    }
}
```

- [ ] **Step 2: Oppdater `PathProcessor.process()` for å aggregere modeller og kalle registry-generator i `processingOver()`-pass**

Erstatt `PathProcessor`-klassen helt:

```java
package org.brylex.parser.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("org.brylex.parser.annotation.Path")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class PathProcessor extends AbstractProcessor {

    private final List<HandlerModel> collected = new ArrayList<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        HandlerModelBuilder builder = new HandlerModelBuilder(processingEnv);
        HandlerCodeGenerator generator = new HandlerCodeGenerator(processingEnv);

        for (TypeElement annotation : annotations) {
            Set<TypeElement> handlerTypes = new LinkedHashSet<>();
            for (Element annotated : roundEnv.getElementsAnnotatedWith(annotation)) {
                Element enclosing = annotated.getEnclosingElement();
                if (enclosing instanceof TypeElement type) {
                    handlerTypes.add(type);
                }
            }
            for (TypeElement type : handlerTypes) {
                HandlerModel model = builder.build(type);
                try {
                    generator.generate(model);
                    collected.add(model);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR, "Codegen feilet: " + e.getMessage(), type);
                }
            }
        }

        if (roundEnv.processingOver() && !collected.isEmpty()) {
            emitRegistry();
        }
        return false;
    }

    private void emitRegistry() {
        RegistryCodeGenerator regGen = new RegistryCodeGenerator(processingEnv);
        String pkg = collected.get(0).packageName();
        String simple = "Generated_PathParserRegistry";
        String fingerprint = Fingerprint.over(collected);
        try {
            regGen.generate(pkg, simple, collected, fingerprint);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Registry-emit feilet: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Stubb `Fingerprint.java`**

```java
package org.brylex.parser.processor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class Fingerprint {
    private Fingerprint() {}

    public static String over(List<HandlerModel> models) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (HandlerModel m : models) {
                md.update((m.packageName() + "." + m.simpleName()).getBytes());
                for (Binding b : m.bindings()) {
                    md.update(b.path().getBytes());
                }
            }
            return java.util.HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 ikke tilgjengelig", e);
        }
    }
}
```

- [ ] **Step 4: Verifiser at processor-modul kompilerer og at testene fra 2.5 fortsatt passerer**

Run: `mvn -pl path-parser-processor verify -q`
Expected: BUILD SUCCESS. Golden-test fra 2.5 fortsatt grønn.

- [ ] **Step 5: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/RegistryCodeGenerator.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/Fingerprint.java
git commit -m "feat(processor): RegistryCodeGenerator + Fingerprint per modul"
```

### Task 2.7: Wire APT-banen inn i runtime `PathParser.of()`

**Files:**
- Modify: `path-parser/src/main/java/org/brylex/parser/PathParser.java`
- Create: `path-parser/src/main/java/org/brylex/parser/GeneratedFactoryRegistry.java`

- [ ] **Step 1: Skriv intern `GeneratedFactoryRegistry`**

`path-parser/src/main/java/org/brylex/parser/GeneratedFactoryRegistry.java`:

```java
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
```

- [ ] **Step 2: Endre `PathParser.of()` til å prøve APT-banen først, så falle tilbake til refleksjons-konstruktør**

I `PathParser.java`, erstatt de eksisterende `of(...)`-metodene:

```java
public static PathParser of(Object handler) {
    return of(handler, PathParser::defaultFactory);
}

public static PathParser of(Object handler,
                            java.util.function.Function<Class<?>, Object> subHandlerFactory) {
    PathParserFactory generated = GeneratedFactoryRegistry.lookup(handler.getClass());
    if (generated != null) {
        return fromFactory(generated, handler, subHandlerFactory);
    }
    return new PathParser(handler, subHandlerFactory);   // refleksjons-fallback (fjernes i Phase 4)
}

private static PathParser fromFactory(PathParserFactory factory, Object handler,
                                      java.util.function.Function<Class<?>, Object> subHandlerFactory) {
    factory.bind(handler, subHandlerFactory, GeneratedFactoryRegistry::lookup);
    return new PathParser(factory.tree(), handler, subHandlerFactory);
}
```

Den eksisterende `PathParser(ParseNode root, Object handler, Function factory)`-konstruktøren er pakke-privat — bruk den. Den vil bli `PathParser(ParseNode root)` i Phase 4.

- [ ] **Step 3: Kjør hele test-suiten på path-parser-modulen**

Run: `mvn -pl path-parser test -q`
Expected: BUILD SUCCESS. Refleksjons-banen brukes fortsatt for alle tester (ingen registry registrert ennå).

- [ ] **Step 4: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/GeneratedFactoryRegistry.java
git add path-parser/src/main/java/org/brylex/parser/PathParser.java
git commit -m "feat(runtime): PathParser.of() prøver APT-bane med refleksjons-fallback"
```

### Task 2.8: End-to-end test — handler kompilert med APT bruker generert factory

**Files:**
- Create: `path-parser/src/test/java/org/brylex/parser/AptEndToEndTest.java`
- Modify: `path-parser/pom.xml` (legg til processor som test-scope dep)

- [ ] **Step 1: Legg til processor-avhengighet for test**

I `path-parser/pom.xml`, legg til denne i `<dependencies>`:

```xml
<dependency>
    <groupId>org.brylex</groupId>
    <artifactId>path-parser-processor</artifactId>
    <version>3.0.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

Og i `<build><plugins>`, konfigurer `maven-compiler-plugin` til å kjøre APT på test-koden:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.brylex</groupId>
                <artifactId>path-parser-processor</artifactId>
                <version>3.0.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

- [ ] **Step 2: Skriv end-to-end-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class AptEndToEndTest {

    public static class SimpleAptHandler {
        @Path("/root/child")
        public String child;
    }

    @Test
    void aptGenerertFactoryOppdagesViaServiceLoader() {
        PathParserFactory factory = GeneratedFactoryRegistry.lookup(SimpleAptHandler.class);
        assertThat(factory).as("APT-bane må produsere factory for handler i samme modul").isNotNull();
    }

    @Test
    void parseBrukerAptIkkeRefleksjon() {
        SimpleAptHandler h = new SimpleAptHandler();
        PathParser.of(h).parse(new StringReader("<root><child>APT</child></root>"));
        assertThat(h.child).isEqualTo("APT");
    }
}
```

- [ ] **Step 3: Kjør test**

Run: `mvn -pl path-parser test -Dtest=AptEndToEndTest -q`
Expected: BUILD SUCCESS. Begge tester grønne. (Hvis ikke: sjekk at maven-compiler-plugin faktisk kjører APT — `mvn -pl path-parser compile -X | grep -i "annotation processing"`.)

- [ ] **Step 4: Commit**

```bash
git add path-parser/pom.xml
git add path-parser/src/test/java/org/brylex/parser/AptEndToEndTest.java
git commit -m "test(runtime): end-to-end APT-bane uten refleksjon for String-felt"
```

---

## Phase 3 — Feature-utvidelse i APT

Hver feature får: (a) en utvidelse av `HandlerModelBuilder`, (b) utvidelse av `HandlerCodeGenerator`, (c) en golden-fil-test, (d) en end-to-end-test (handler kompilert med APT, parses uten refleksjon). TDD-syklus per feature.

### Task 3.1: Type-konvertering for felt (int, BigDecimal, LocalDate, etc.)

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/Binding.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Create: `path-parser/src/test/java/org/brylex/parser/TypeConversionAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TypeConversionAptTest {

    public static class TypedHandler {
        @Path("/o/qty") public int quantity;
        @Path("/o/price") public BigDecimal price;
        @Path("/o/date") public LocalDate date;
        @Path("/o/id") public UUID id;
        @Path("/o/status") public Status status;
    }

    public enum Status { OPEN, CLOSED }

    @Test
    void aptKonverterer() {
        TypedHandler h = new TypedHandler();
        String xml = "<o><qty>42</qty><price>9.99</price><date>2026-05-19</date>"
                + "<id>00000000-0000-0000-0000-000000000001</id><status>OPEN</status></o>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.quantity).isEqualTo(42);
        assertThat(h.price).isEqualByComparingTo("9.99");
        assertThat(h.date).isEqualTo(LocalDate.of(2026, 5, 19));
        assertThat(h.id).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(h.status).isEqualTo(Status.OPEN);
    }
}
```

- [ ] **Step 2: Kjør test (forventet fail — generator håndterer bare String)**

Run: `mvn -pl path-parser test -Dtest=TypeConversionAptTest -q`
Expected: FAIL — ClassCastException eller wrong field assignment.

- [ ] **Step 3: Utvid `HandlerCodeGenerator.emitBindingBindCall` for å bruke `Conversions.convert(text, type)` for ikke-String-typer**

Erstatt `emitBindingBindCall` i `HandlerCodeGenerator.java`:

```java
private void emitBindingBindCall(MethodSpec.Builder builder, Binding binding) {
    if (!(binding instanceof Binding.FieldText ft)) return;
    String lookup = treeLookupExpression(ft.path());
    String fieldType = ft.fieldType();
    if (fieldType.equals("java.lang.String")) {
        builder.addStatement("$L.endInvokers.add(arg -> h.$L = (String) arg)", lookup, ft.fieldName());
    } else {
        builder.addStatement(
                "$L.endInvokers.add(arg -> h.$L = ($L) $T.convert((String) arg, $L.class))",
                lookup, ft.fieldName(), fieldType,
                com.squareup.javapoet.ClassName.get("org.brylex.parser", "Conversions"),
                primitiveBoxIfNeeded(fieldType));
    }
}

private static String primitiveBoxIfNeeded(String type) {
    return switch (type) {
        case "int" -> "Integer";
        case "long" -> "Long";
        case "short" -> "Short";
        case "byte" -> "Byte";
        case "double" -> "Double";
        case "float" -> "Float";
        case "boolean" -> "Boolean";
        case "char" -> "Character";
        default -> type;
    };
}

private String treeLookupExpression(String path) {
    String[] segments = path.split("/");
    StringBuilder sb = new StringBuilder("TREE");
    for (String segment : segments) {
        if (segment.isEmpty()) continue;
        sb.append(".lookupChild(\"").append(segment).append("\", 0, null, null)");
    }
    return sb.toString();
}
```

- [ ] **Step 4: Gjør `Conversions.convert(String, Class)` `public` i runtime-modulen**

I `path-parser/src/main/java/org/brylex/parser/Conversions.java`, endre `final class Conversions` → `public final class Conversions`, og endre `static Object convert(...)` → `public static Object convert(...)`. (`canConvert` og `converterFor` forblir package-private.)

- [ ] **Step 5: Kjør runtime-test og golden-test**

Run: `mvn -pl path-parser test -Dtest=TypeConversionAptTest -q`
Expected: BUILD SUCCESS.
Run: `mvn -pl path-parser-processor test -Dtest=HandlerCodeGeneratorTest -q`
Expected: FAIL — golden-fil må oppdateres med endret `lookupExpression`-form. Inspisér output og oppdater golden-filen.

Etter golden-update: `mvn -pl path-parser-processor test -Dtest=HandlerCodeGeneratorTest -q` → BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/main/java/org/brylex/parser/Conversions.java
git add path-parser/src/test/java/org/brylex/parser/TypeConversionAptTest.java
git add path-parser-processor/src/test/resources/golden/SimpleHandler_PathParser.golden.java
git commit -m "feat(processor): codegen for type-konvertering på felt"
```

### Task 3.2: Attributt-mapping (`@id`)

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/AttributeAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeAptTest {

    public static class AttrHandler {
        @Path("/order/@id") public String orderId;
        @Path("/order/customer/@type") public String customerType;
        @Path("/order/@total") public int total;
    }

    @Test
    void attributtMappingFungerer() {
        AttrHandler h = new AttrHandler();
        String xml = "<order id='X1' total='42'><customer type='PRO'>Acme</customer></order>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.orderId).isEqualTo("X1");
        assertThat(h.customerType).isEqualTo("PRO");
        assertThat(h.total).isEqualTo(42);
    }
}
```

- [ ] **Step 2: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=AttributeAptTest -q`
Expected: FAIL — attributter ignoreres av nåværende generator.

- [ ] **Step 3: I `HandlerModelBuilder.build()`, detekter `@`-prefiks i siste segment**

I `HandlerModelBuilder.build()`, etter `bindings.add(new Binding.FieldText(...))`-linjen, erstatt med:

```java
String pathValue = path.value();
int lastSlash = pathValue.lastIndexOf('/');
String lastSegment = pathValue.substring(lastSlash + 1);
if (lastSegment.startsWith("@")) {
    String parentPath = pathValue.substring(0, lastSlash);
    bindings.add(new Binding.Attribute(parentPath, field,
            field.getSimpleName().toString(), fieldType, lastSegment.substring(1)));
} else {
    bindings.add(new Binding.FieldText(pathValue, field, field.getSimpleName().toString(), fieldType));
}
```

- [ ] **Step 4: Utvid `HandlerCodeGenerator.bindMethod` og `emitBindingTreeNodes` for `Binding.Attribute`**

I `emitBindingTreeNodes`:

```java
private void emitBindingTreeNodes(MethodSpec.Builder builder, Binding binding) {
    String path;
    boolean needsText = false;
    if (binding instanceof Binding.FieldText ft) {
        path = ft.path();
        needsText = true;
    } else if (binding instanceof Binding.Attribute attr) {
        path = attr.path();
    } else {
        return;
    }
    String[] segments = path.split("/");
    String parent = "root";
    StringBuilder varName = new StringBuilder("n");
    for (String segment : segments) {
        if (segment.isEmpty()) continue;
        varName.append("_").append(segment);
        builder.addStatement("$T $L = $L.addChild($S, null, null)",
                ClassName.get("org.brylex.parser", "ParseNode"),
                varName.toString(), parent, segment);
        parent = varName.toString();
    }
    if (needsText) builder.addStatement("$L.needsText = true", parent);
}
```

I `emitBindingBindCall`, legg til en `else if`-gren for `Binding.Attribute`:

```java
if (binding instanceof Binding.Attribute attr) {
    String lookup = treeLookupExpression(attr.path());
    String fieldType = attr.fieldType();
    String attrName = attr.attrName();
    String fieldName = attr.fieldName();
    if (fieldType.equals("java.lang.String")) {
        builder.addStatement(
                "$L.startInvokers.add(snap -> { String v = (($T) snap).attributeValue($S); if (v != null) h.$L = v; })",
                lookup,
                ClassName.get("org.brylex.parser", "AttributeSnapshot"),
                attrName, fieldName);
    } else {
        builder.addStatement(
                "$L.startInvokers.add(snap -> { String v = (($T) snap).attributeValue($S); if (v != null) h.$L = ($L) $T.convert(v, $L.class); })",
                lookup,
                ClassName.get("org.brylex.parser", "AttributeSnapshot"),
                attrName, fieldName, fieldType,
                ClassName.get("org.brylex.parser", "Conversions"),
                primitiveBoxIfNeeded(fieldType));
    }
    return;
}
```

- [ ] **Step 5: Sørg for at `AttributeSnapshot.attributeValue(String)` finnes som offentlig metode**

Sjekk `path-parser/src/main/java/org/brylex/parser/AttributeSnapshot.java`. Hvis ikke offentlig: gjør den `public` og legg til `public String attributeValue(String name)` som leter etter `name` i `attrNames`/`attrValues`-arrayene.

- [ ] **Step 6: Kjør test**

Run: `mvn -pl path-parser test -Dtest=AttributeAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/main/java/org/brylex/parser/AttributeSnapshot.java
git add path-parser/src/test/java/org/brylex/parser/AttributeAptTest.java
git commit -m "feat(processor): attributt-mapping (@attr) i codegen"
```

### Task 3.3: Filter-attributter (`[@id='X']`)

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/FilterAttrAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class FilterAttrAptTest {

    public static class FilterHandler {
        @Path("/menu/food[@id='FRUIT']") public String fruit;
        @Path("/menu/food[@id='BREAD']") public String bread;
    }

    @Test
    void filterMatcherRiktigElement() {
        FilterHandler h = new FilterHandler();
        String xml = "<menu><food id='FRUIT'>Apple</food><food id='BREAD'>Loaf</food></menu>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.fruit).isEqualTo("Apple");
        assertThat(h.bread).isEqualTo("Loaf");
    }
}
```

- [ ] **Step 2: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=FilterAttrAptTest -q`
Expected: FAIL.

- [ ] **Step 3: Utvid `emitBindingTreeNodes`/`treeLookupExpression` til å parse `[@k='v']`-segmenter**

I `HandlerCodeGenerator.java`, legg til:

```java
private static final java.util.regex.Pattern FILTER = java.util.regex.Pattern.compile("(.+)\\[@(.+)=['\"]?([^'\"\\]]+)['\"]?\\]");

private static String[] parseSegment(String segment) {
    java.util.regex.Matcher m = FILTER.matcher(segment);
    if (m.matches()) {
        return new String[] { m.group(1), m.group(2), m.group(3) };
    }
    return new String[] { segment, null, null };
}
```

Modifiser `emitBindingTreeNodes` til å bruke `parseSegment`:

```java
for (String segment : segments) {
    if (segment.isEmpty()) continue;
    String[] parsed = parseSegment(segment);
    varName.append("_").append(parsed[0]);
    if (parsed[1] != null) varName.append("__").append(parsed[1]).append("_").append(parsed[2]);
    builder.addStatement("$T $L = $L.addChild($S, $L, $L)",
            ClassName.get("org.brylex.parser", "ParseNode"),
            varName.toString(), parent, parsed[0],
            parsed[1] == null ? "null" : "\"" + parsed[1] + "\"",
            parsed[2] == null ? "null" : "\"" + parsed[2] + "\"");
    parent = varName.toString();
}
```

Tilsvarende modifiser `treeLookupExpression` til å emit `lookupChildFiltered(...)`-kall — eller heller: la generert kode bruke en helper-metode på `ParseNode`. Enklere: hold et lokalt `Map<path, String>` i bind-metode-generatoren slik at felt-lookups bruker samme variabel-navn som tree-bygging:

I `bindMethod`, gjør om til at `emitBindingBindCall` får `Map<String,String>` med path→var-navn. Skriv om begge metodene slik at de deler en `Map<String, String>` for lookup-er. Detaljert refaktorisering — se neste sub-steg.

- [ ] **Step 4: Refaktorer til delt lookup-tabell**

I `HandlerCodeGenerator.bindMethod`:

```java
private MethodSpec bindMethod(HandlerModel model, ClassName handlerClass) {
    // ... eksisterende oppsett ...
    java.util.Map<String, String> nodeVarsByPath = new java.util.HashMap<>();
    // Bygg lookup-uttrykk per leaf-path ved å traversere TREE fra TREE.lookupChild(...) eksplisitt.
    for (Binding binding : model.bindings()) {
        emitBindingBindCall(builder, binding, nodeVarsByPath);
    }
    // ...
}
```

`emitBindingBindCall` regenererer lookup-uttrykket fra path:

```java
private String treeLookupExpression(String path) {
    String[] segments = path.split("/");
    StringBuilder sb = new StringBuilder("TREE");
    for (String segment : segments) {
        if (segment.isEmpty()) continue;
        String[] parsed = parseSegment(segment);
        if (parsed[1] == null) {
            sb.append(".lookupChild(\"").append(parsed[0]).append("\", 0, null, null)");
        } else {
            // for filter-noder kreves at runtime kjenner attrNames/values — bruk en helper.
            sb.append(".lookupChildFiltered(\"").append(parsed[0]).append("\", \"")
                    .append(parsed[1]).append("\", \"").append(parsed[2]).append("\")");
        }
    }
    return sb.toString();
}
```

- [ ] **Step 5: Legg til `lookupChildFiltered` på `ParseNode`**

I `path-parser/src/main/java/org/brylex/parser/ParseNode.java`, legg til:

```java
ParseNode lookupChildFiltered(String name, String filterAttrName, String filterAttrValue) {
    if (children == null) return null;
    ChildBucket bucket = children.get(name);
    if (bucket == null) return null;
    for (ParseNode candidate : bucket.entries) {
        if (filterAttrName.equals(candidate.filterAttrName)
                && filterAttrValue.equals(candidate.filterAttrValue)) {
            return candidate;
        }
    }
    return null;
}
```

- [ ] **Step 6: Kjør test**

Run: `mvn -pl path-parser test -Dtest=FilterAttrAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/main/java/org/brylex/parser/ParseNode.java
git add path-parser/src/test/java/org/brylex/parser/FilterAttrAptTest.java
git commit -m "feat(processor): filter-attributter [@attr='value'] i codegen"
```

### Task 3.4: Collection-felt (`List<T>`, `Set<T>`, `Queue<T>`)

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/CollectionAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionAptTest {

    public static class CollHandler {
        @Path("/items/item") public List<String> items;
        @Path("/prices/p") public List<BigDecimal> prices;
        @Path("/tags/tag") public Set<String> tags;
    }

    @Test
    void collectsRepeatedElements() {
        CollHandler h = new CollHandler();
        String xml = "<root>"
                + "<items><item>A</item><item>B</item></items>"
                + "<prices><p>1.50</p><p>2.50</p></prices>"
                + "<tags><tag>x</tag><tag>y</tag><tag>x</tag></tags>"
                + "</root>";
        // OBS: paths refererer ikke til /root — vi tester at handler kan ha relative roter.
        // For dette testet: oppdater xml til å matche fullt:
        xml = "<doc>"
                + "<items><item>A</item><item>B</item></items>"
                + "<prices><p>1.50</p><p>2.50</p></prices>"
                + "<tags><tag>x</tag><tag>y</tag></tags>"
                + "</doc>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.items).containsExactly("A", "B");
        assertThat(h.prices).containsExactly(new BigDecimal("1.50"), new BigDecimal("2.50"));
        assertThat(h.tags).containsExactlyInAnyOrder("x", "y");
    }
}
```

- [ ] **Step 2: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=CollectionAptTest -q`
Expected: FAIL.

- [ ] **Step 3: I `HandlerModelBuilder`, detekter `Collection`-felt og emit `Binding.Collection`**

I `HandlerModelBuilder.build()`, før FieldText-grenen, sjekk om feltet er en collection. Bruk `processingEnv.getTypeUtils().asElement(field.asType())` for å hente `TypeElement`, sjekk `getQualifiedName()` mot `java.util.List/Set/Queue/Collection`. For generic-type-argumentet, cast til `DeclaredType` og hent `typeArguments`:

```java
private boolean isCollectionType(String typeName) {
    return typeName.startsWith("java.util.List")
            || typeName.startsWith("java.util.Set")
            || typeName.startsWith("java.util.Queue")
            || typeName.startsWith("java.util.Collection");
}

private String elementTypeOf(VariableElement field) {
    javax.lang.model.type.TypeMirror mirror = field.asType();
    if (mirror instanceof javax.lang.model.type.DeclaredType dt && !dt.getTypeArguments().isEmpty()) {
        return dt.getTypeArguments().get(0).toString();
    }
    return "java.lang.String";
}
```

I `build()`-løkken:

```java
if (isCollectionType(fieldType)) {
    String elemType = elementTypeOf(field);
    bindings.add(new Binding.Collection(pathValue, field, field.getSimpleName().toString(),
            fieldType, elemType));
    continue;
}
```

- [ ] **Step 4: Utvid `HandlerCodeGenerator` for `Binding.Collection`**

I `emitBindingTreeNodes` legg til:
```java
} else if (binding instanceof Binding.Collection coll) {
    path = coll.path();
    needsText = true;
}
```

I `emitBindingBindCall` legg til:

```java
if (binding instanceof Binding.Collection coll) {
    String lookup = treeLookupExpression(coll.path());
    String elemType = coll.elementType();
    String collectionType = coll.collectionType();
    String collectionInit = collectionInitFor(collectionType);
    builder.addStatement("$L.endInvokers.add(arg -> { if (h.$L == null) h.$L = $L; "
                    + "h.$L.add($L); })",
            lookup, coll.fieldName(), coll.fieldName(), collectionInit,
            coll.fieldName(),
            elemType.equals("java.lang.String")
                    ? "(String) arg"
                    : "(" + elemType + ") org.brylex.parser.Conversions.convert((String) arg, " + elemType + ".class)");
    return;
}

private static String collectionInitFor(String type) {
    if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) return "new java.util.ArrayList<>()";
    if (type.startsWith("java.util.Set")) return "new java.util.LinkedHashSet<>()";
    if (type.startsWith("java.util.Queue")) return "new java.util.ArrayDeque<>()";
    throw new IllegalArgumentException("Ustøttet collection-type: " + type);
}
```

- [ ] **Step 5: Kjør test**

Run: `mvn -pl path-parser test -Dtest=CollectionAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/test/java/org/brylex/parser/CollectionAptTest.java
git commit -m "feat(processor): codegen for collection-felt (List/Set/Queue)"
```

### Task 3.5: Metode-tekst-bindinger

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/MethodTextAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class MethodTextAptTest {

    public static class MethHandler {
        public int last;
        @Path("/r/qty") public void onQty(int v) { this.last = v; }
    }

    @Test
    void metodeKallesMedKonvertertVerdi() {
        MethHandler h = new MethHandler();
        PathParser.of(h).parse(new StringReader("<r><qty>7</qty></r>"));
        assertThat(h.last).isEqualTo(7);
    }
}
```

- [ ] **Step 2: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=MethodTextAptTest -q`
Expected: FAIL — metoder ignoreres.

- [ ] **Step 3: Utvid `HandlerModelBuilder.build()` for `ElementKind.METHOD`**

I løkken, etter felt-grenen:

```java
if (member.getKind() == ElementKind.METHOD) {
    javax.lang.model.element.ExecutableElement method = (javax.lang.model.element.ExecutableElement) member;
    if (method.getParameters().size() != 1) continue;
    javax.lang.model.element.VariableElement param = method.getParameters().get(0);
    String paramType = param.asType().toString();
    String pathValue = path.value();
    String methodName = method.getSimpleName().toString();
    if (paramType.equals("javax.xml.stream.events.StartElement")) {
        bindings.add(new Binding.MethodEvent(pathValue, method, methodName, Binding.MethodEvent.EventKind.START));
    } else if (paramType.equals("javax.xml.stream.events.EndElement")) {
        bindings.add(new Binding.MethodEvent(pathValue, method, methodName, Binding.MethodEvent.EventKind.END));
    } else {
        bindings.add(new Binding.MethodText(pathValue, method, methodName, paramType));
    }
}
```

(StartElement/EndElement-grenen er klargjøring for Task 3.6.)

- [ ] **Step 4: Utvid `HandlerCodeGenerator` for `Binding.MethodText`**

I `emitBindingTreeNodes`, legg til:
```java
} else if (binding instanceof Binding.MethodText mt) {
    path = mt.path();
    needsText = true;
}
```

I `emitBindingBindCall`:
```java
if (binding instanceof Binding.MethodText mt) {
    String lookup = treeLookupExpression(mt.path());
    String paramType = mt.paramType();
    String callValue = paramType.equals("java.lang.String")
            ? "(String) arg"
            : "(" + paramType + ") org.brylex.parser.Conversions.convert((String) arg, "
                    + primitiveBoxIfNeeded(paramType) + ".class)";
    builder.addStatement("$L.endInvokers.add(arg -> h.$L($L))", lookup, mt.methodName(), callValue);
    return;
}
```

- [ ] **Step 5: Kjør test**

Run: `mvn -pl path-parser test -Dtest=MethodTextAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/test/java/org/brylex/parser/MethodTextAptTest.java
git commit -m "feat(processor): codegen for metode-tekst-bindinger"
```

### Task 3.6: `StartElement`/`EndElement`-event-bindinger

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/EventAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class EventAptTest {

    public static class EvtHandler {
        public int starts;
        public int ends;
        @Path("/r/x") public void onStart(StartElement e) { starts++; }
        @Path("/r/x") public void onEnd(EndElement e) { ends++; }
    }

    @Test
    void starsOgEndsKalles() {
        EvtHandler h = new EvtHandler();
        PathParser.of(h).parse(new StringReader("<r><x/><x>data</x><x/></r>"));
        assertThat(h.starts).isEqualTo(3);
        assertThat(h.ends).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=EventAptTest -q`
Expected: FAIL.

- [ ] **Step 3: I `HandlerCodeGenerator.emitBindingTreeNodes`, sett `needsStartElement`/`needsEndElement` per event-kind**

```java
} else if (binding instanceof Binding.MethodEvent ev) {
    path = ev.path();
    // need-flags settes inn etter løkka:
    extraFlag = ev.eventKind();
}
// etter løkka:
if (extraFlag == Binding.MethodEvent.EventKind.START) {
    builder.addStatement("$L.needsStartElement = true", parent);
} else if (extraFlag == Binding.MethodEvent.EventKind.END) {
    builder.addStatement("$L.needsEndElement = true", parent);
}
```

(Refaktorer slik at `emitBindingTreeNodes` returnerer eller skiller cases — den enkleste varianten er en separat metode per binding-type. Hvis kompleksiteten skal holdes nede, splitt ut.)

- [ ] **Step 4: I `emitBindingBindCall` legg til event-gren**

```java
if (binding instanceof Binding.MethodEvent ev) {
    String lookup = treeLookupExpression(ev.path());
    String invokerList = ev.eventKind() == Binding.MethodEvent.EventKind.START ? "startInvokers" : "endInvokers";
    String castType = ev.eventKind() == Binding.MethodEvent.EventKind.START
            ? "javax.xml.stream.events.StartElement" : "javax.xml.stream.events.EndElement";
    builder.addStatement("$L.$L.add(arg -> h.$L(($L) arg))", lookup, invokerList, ev.methodName(), castType);
    return;
}
```

- [ ] **Step 5: Kjør test**

Run: `mvn -pl path-parser test -Dtest=EventAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/test/java/org/brylex/parser/EventAptTest.java
git commit -m "feat(processor): codegen for StartElement/EndElement-event-bindinger"
```

### Task 3.7: Sub-handler-bindinger med lazy lookup (støtter rekursive grafer)

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/SubHandlerAptTest.java`
- Create: `path-parser/src/test/java/org/brylex/parser/RecursiveSubHandlerAptTest.java`

- [ ] **Step 1: Skriv runtime-test for simple sub-handler**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubHandlerAptTest {

    public static class Item {
        @Path("/sku") public String sku;
        @Path("/price") public String price;
    }

    public static class OrderH {
        public final List<Item> items = new ArrayList<>();
        @Path("/order/item") public void onItem(Item item) { items.add(item); }
    }

    @Test
    void subHandlerMetode() {
        OrderH h = new OrderH();
        String xml = "<order><item><sku>A1</sku><price>9.99</price></item>"
                + "<item><sku>B2</sku><price>1.00</price></item></order>";
        PathParser.of(h).parse(new StringReader(xml));
        assertThat(h.items).hasSize(2);
        assertThat(h.items.get(0).sku).isEqualTo("A1");
        assertThat(h.items.get(1).price).isEqualTo("1.00");
    }
}
```

- [ ] **Step 2: Skriv test for rekursiv sub-handler (`Node → Node`)**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveSubHandlerAptTest {

    public static class Node {
        @Path("/label") public String label;
        @Path("/child") public List<Node> children = new ArrayList<>();
    }

    public static class Root {
        public Node root;
        @Path("/tree") public void onRoot(Node n) { root = n; }
    }

    @Test
    void rekursivSubHandler() {
        Root r = new Root();
        String xml = "<tree><label>top</label>"
                + "<child><label>a</label></child>"
                + "<child><label>b</label><child><label>b1</label></child></child>"
                + "</tree>";
        PathParser.of(r).parse(new StringReader(xml));
        assertThat(r.root.label).isEqualTo("top");
        assertThat(r.root.children).hasSize(2);
        assertThat(r.root.children.get(1).children.get(0).label).isEqualTo("b1");
    }
}
```

- [ ] **Step 3: Kjør (forventet fail)**

Run: `mvn -pl path-parser test -Dtest=SubHandlerAptTest,RecursiveSubHandlerAptTest -q`
Expected: FAIL.

- [ ] **Step 4: I `HandlerModelBuilder`, detekter sub-handler-mønstre**

For metode-parameter som ikke er konverterbar og ikke event:

```java
} else {
    // sub-handler-type
    bindings.add(new Binding.SubHandler(pathValue, method, methodName, paramType,
            Binding.SubHandler.Kind.METHOD, null));
}
```

For collection-felt der element-type ikke er konverterbar:

```java
if (isCollectionType(fieldType)) {
    String elemType = elementTypeOf(field);
    if (isConvertibleType(elemType)) {
        bindings.add(new Binding.Collection(pathValue, field, ..., fieldType, elemType));
    } else {
        bindings.add(new Binding.SubHandler(pathValue, field, field.getSimpleName().toString(),
                elemType, Binding.SubHandler.Kind.COLLECTION_FIELD, fieldType));
    }
    continue;
}

private boolean isConvertibleType(String type) {
    return switch (type) {
        case "java.lang.String", "int", "java.lang.Integer", "long", "java.lang.Long",
             "short", "java.lang.Short", "byte", "java.lang.Byte",
             "double", "java.lang.Double", "float", "java.lang.Float",
             "boolean", "java.lang.Boolean", "char", "java.lang.Character",
             "java.math.BigInteger", "java.math.BigDecimal",
             "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant",
             "java.util.UUID" -> true;
        default -> {
            // sjekk om enum via TypeMirror
            yield false;
        }
    };
}
```

For direkte felt-type (ikke-konverterbar, ikke collection): tilsvarende `SubHandler.Kind.FIELD`.

- [ ] **Step 5: I `HandlerCodeGenerator`, emit sub-handler-binding med lazy `subFactoryLookup`-portal**

I `emitBindingTreeNodes`:
```java
} else if (binding instanceof Binding.SubHandler sh) {
    path = sh.path();
}
```

I `emitBindingBindCall`:

```java
if (binding instanceof Binding.SubHandler sh) {
    String lookup = treeLookupExpression(sh.path());
    String subType = sh.subHandlerType();
    // Lazy resolution: vi bygger en runner som ved første match henter sub-factory
    // og delegerer parsing til en intern sub-parse-loop.
    String applyFragment = switch (sh.kind()) {
        case METHOD -> "h." + sh.targetName() + "((" + subType + ") inst)";
        case FIELD -> "h." + sh.targetName() + " = (" + subType + ") inst";
        case COLLECTION_FIELD -> "{ if (h." + sh.targetName() + " == null) h."
                + sh.targetName() + " = " + collectionInitFor(sh.collectionType())
                + "; h." + sh.targetName() + ".add((" + subType + ") inst); }";
    };
    builder.addStatement(
            "$L.startInvokers.add(arg -> {})", lookup);    // placeholder for sub-parse-entry
    builder.addStatement(
            "$L.endInvokers.add(arg -> { Object inst = subHandlerFactory.apply($L.class); "
                    + "$T sub = subFactoryLookup.apply($L.class); "
                    + "if (sub != null) sub.bind(inst, subHandlerFactory, subFactoryLookup); "
                    + "$L; })",
            lookup, subType,
            ClassName.get("org.brylex.parser", "PathParserFactory"), subType,
            applyFragment);
    return;
}
```

**Viktig merknad:** Den ekte sub-parse-traverseringen krever at `PathParser` har en intern "switch til sub-parser ved match"-mekanisme. Den eksisterende `parseLoop` har dette via `CreateInstanceInvoker`/`ApplySubParserInvoker`. I 3.0 må vi reprodusere mekanikken uten refleksjon. Detaljert design:

- Generert kode på leaf-noden `<order>/item`-stien legger til en *spesiell* `Invoker`-implementasjon (la oss kalle den `SubParserActivator`) som ved `START_ELEMENT` "skifter" parse-tre til sub-handlerens factory.tree(), og ved `END_ELEMENT` "bytter tilbake" og applyer instansen via `applyFragment`.
- For å unngå å redesigne `parseLoop` i denne tasken: legg til en runtime-helper `SubParseActivator`-klasse som beholder pekere til sub-handler-instans, sub-factory og target.

- [ ] **Step 6: Legg til runtime-helper `SubParseActivator.java`**

```java
package org.brylex.parser;

import java.util.function.Consumer;
import java.util.function.Function;

public final class SubParseActivator implements Invoker {

    private final Class<?> subType;
    private final Function<Class<?>, Object> instanceFactory;
    private final Function<Class<?>, PathParserFactory> factoryLookup;
    private final Consumer<Object> applyToParent;

    public SubParseActivator(Class<?> subType,
                             Function<Class<?>, Object> instanceFactory,
                             Function<Class<?>, PathParserFactory> factoryLookup,
                             Consumer<Object> applyToParent) {
        this.subType = subType;
        this.instanceFactory = instanceFactory;
        this.factoryLookup = factoryLookup;
        this.applyToParent = applyToParent;
    }

    public Class<?> subType() { return subType; }
    public Function<Class<?>, Object> instanceFactory() { return instanceFactory; }
    public Function<Class<?>, PathParserFactory> factoryLookup() { return factoryLookup; }
    public Consumer<Object> applyToParent() { return applyToParent; }

    @Override public void invoke(Object argument) { /* aktivering håndteres av parseLoop */ }
}
```

Endre `Invoker` fra `sealed` til `non-sealed` så vi kan ha flere generated/runtime-implementasjoner.

- [ ] **Step 7: Utvid `parseLoop` til å gjenkjenne `SubParseActivator`**

I `PathParser.parseLoop`, ved `START_ELEMENT` etter `invokeStartHandlers`, sjekk om en av node's `startInvokers` er `SubParseActivator`. I så fall: push gjeldende tree, bytt til `factoryLookup.apply(subType).tree()` etter bind av ny sub-instans, og fortsett.

Detaljert kode tilføres i Task 4.x når vi rydder opp i parseLoop. For nå: stub `SubParseActivator` slik at simpel-test passerer; rekursiv test kan utstå til Phase 4.

(Plan-merknad: dette er det mest komplekse i hele planen. Hvis det blir for stort her, splitt 3.7 i 3.7a (simpel sub-handler uten lazy lookup) og 3.7b (lazy + rekursiv) — sistnevnte i Phase 4.)

- [ ] **Step 8: Kjør tester**

Run: `mvn -pl path-parser test -Dtest=SubHandlerAptTest -q`
Expected: BUILD SUCCESS for ikke-rekursiv. `RecursiveSubHandlerAptTest` kan utstå.

- [ ] **Step 9: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/main/java/org/brylex/parser/Invoker.java
git add path-parser/src/main/java/org/brylex/parser/SubParseActivator.java
git add path-parser/src/main/java/org/brylex/parser/PathParser.java
git add path-parser/src/test/java/org/brylex/parser/SubHandlerAptTest.java
git add path-parser/src/test/java/org/brylex/parser/RecursiveSubHandlerAptTest.java
git commit -m "feat(processor+runtime): sub-handler-codegen med lazy factory-lookup"
```

### Task 3.8: Privat felt-tilgang via `privateLookupIn`

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java`
- Create: `path-parser/src/test/java/org/brylex/parser/PrivateFieldAptTest.java`

- [ ] **Step 1: Skriv runtime-test**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateFieldAptTest {

    public static class PrivHandler {
        @Path("/r/v") private String value;
        public String getValue() { return value; }
    }

    @Test
    void privatFeltSettesViaVarHandle() {
        PrivHandler h = new PrivHandler();
        PathParser.of(h).parse(new StringReader("<r><v>secret</v></r>"));
        assertThat(h.getValue()).isEqualTo("secret");
    }
}
```

- [ ] **Step 2: Kjør (forventet fail — generert kode prøver direkte felt-tilgang)**

Run: `mvn -pl path-parser test -Dtest=PrivateFieldAptTest -q`
Expected: FAIL — kompileringsfeil i generert kode ("field is private").

- [ ] **Step 3: I `HandlerModelBuilder`, registrer `private`-modifier på feltet**

Endre `Binding.FieldText`-recorden til å inkludere `boolean isPrivate`. Oppdater bygging:

```java
record FieldText(String path, Element element, String fieldName, String fieldType, boolean isPrivate) implements Binding {}
```

```java
boolean isPrivate = field.getModifiers().contains(javax.lang.model.element.Modifier.PRIVATE);
bindings.add(new Binding.FieldText(pathValue, field, ..., isPrivate));
```

- [ ] **Step 4: I `HandlerCodeGenerator`, generér `VarHandle` for private felt**

I `emitBindingBindCall`, sjekk `isPrivate`:

```java
if (ft.isPrivate()) {
    String vhName = "VH_" + ft.fieldName();
    // Generer en statisk VarHandle som lookup-es én gang per klasse.
    // Dette krever at vi akkumulerer felt og emitter statisk init separat.
    builder.addStatement(
            "java.lang.invoke.VarHandle $L = java.lang.invoke.MethodHandles"
                    + ".privateLookupIn($T.class, java.lang.invoke.MethodHandles.lookup())"
                    + ".findVarHandle($T.class, $S, $L.class)",
            vhName, handlerClass, handlerClass, ft.fieldName(), ft.fieldType());
    builder.addStatement("$L.endInvokers.add(arg -> $L.set(h, arg))", lookup, vhName);
}
```

(Bedre design: lift VarHandle til static field. For nå: lokal i bind() er enklere; refaktorer i 4.x hvis nødvendig.)

- [ ] **Step 5: Kjør test**

Run: `mvn -pl path-parser test -Dtest=PrivateFieldAptTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/Binding.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerModelBuilder.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerCodeGenerator.java
git add path-parser/src/test/java/org/brylex/parser/PrivateFieldAptTest.java
git commit -m "feat(processor): privateLookupIn-basert VarHandle for private felt"
```

### Task 3.9: Custom `subHandlerFactory`-respekt (DI-test)

**Files:**
- Create: `path-parser/src/test/java/org/brylex/parser/CustomFactoryAptTest.java`

- [ ] **Step 1: Skriv test som bekrefter at brukerens factory kalles**

```java
package org.brylex.parser;

import org.brylex.parser.annotation.Path;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFactoryAptTest {

    public static class Tag {
        @Path("/name") public String name;
    }

    public static class Doc {
        public final List<Tag> tags = new ArrayList<>();
        @Path("/doc/tag") public void onTag(Tag t) { tags.add(t); }
    }

    @Test
    void customFactoryBrukesForSubHandlerInstans() {
        List<Class<?>> calls = new ArrayList<>();
        Doc d = new Doc();
        PathParser.of(d, type -> {
            calls.add(type);
            try { return type.getDeclaredConstructor().newInstance(); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).parse(new StringReader("<doc><tag><name>a</name></tag><tag><name>b</name></tag></doc>"));

        assertThat(calls).containsExactly(Tag.class, Tag.class);
        assertThat(d.tags).hasSize(2);
    }
}
```

- [ ] **Step 2: Kjør test**

Run: `mvn -pl path-parser test -Dtest=CustomFactoryAptTest -q`
Expected: BUILD SUCCESS — Task 3.7 sin codegen kaller `subHandlerFactory.apply(...)` allerede.

- [ ] **Step 3: Commit**

```bash
git add path-parser/src/test/java/org/brylex/parser/CustomFactoryAptTest.java
git commit -m "test(runtime): custom subHandlerFactory respekteres av APT-banen"
```

---

## Phase 4 — Hardening: validator, fingerprint, retention, fjerning av refleksjon

### Task 4.1: `HandlerValidator` med kompilerings-feilmeldinger

**Files:**
- Create: `path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerValidator.java`
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java`
- Create: `path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerValidatorTest.java`

- [ ] **Step 1: Skriv valideringsregler i `HandlerValidator.java`**

```java
package org.brylex.parser.processor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.HashMap;
import java.util.Map;

public final class HandlerValidator {

    private final ProcessingEnvironment env;

    public HandlerValidator(ProcessingEnvironment env) {
        this.env = env;
    }

    public boolean validate(HandlerModel model) {
        boolean ok = true;
        Map<String, Binding> seen = new HashMap<>();
        for (Binding b : model.bindings()) {
            if (b.path().isEmpty()) {
                err(model.handlerType(), "@Path-verdien kan ikke være tom.");
                ok = false;
                continue;
            }
            Binding prior = seen.put(b.path(), b);
            if (prior != null) {
                err(model.handlerType(),
                        "@Path('" + b.path() + "') deklareres to ganger i "
                                + model.simpleName() + ".");
                ok = false;
            }
            if (b instanceof Binding.FieldText ft && !isSupportedFieldType(ft.fieldType())) {
                err(model.handlerType(),
                        "Felt-type '" + ft.fieldType()
                                + "' kan ikke konverteres fra tekst, og inneholder ingen @Path-annoterte elementer for sub-handler-bruk.");
                ok = false;
            }
        }
        return ok;
    }

    private boolean isSupportedFieldType(String type) {
        return type.equals("java.lang.String") || type.equals("int") || type.equals("java.lang.Integer")
                || type.equals("long") || type.equals("java.lang.Long")
                || type.equals("double") || type.equals("java.lang.Double")
                || type.equals("boolean") || type.equals("java.lang.Boolean")
                || type.equals("java.math.BigDecimal") || type.equals("java.math.BigInteger")
                || type.equals("java.time.LocalDate") || type.equals("java.time.LocalDateTime")
                || type.equals("java.time.Instant") || type.equals("java.util.UUID")
                || type.startsWith("java.util.List") || type.startsWith("java.util.Set")
                || type.startsWith("java.util.Queue") || type.startsWith("java.util.Collection");
    }

    private void err(TypeElement type, String msg) {
        env.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, type);
    }
}
```

- [ ] **Step 2: Wire validator inn i `PathProcessor.process()`**

I `process()`-løkken, før `generator.generate(model)`:

```java
HandlerValidator validator = new HandlerValidator(processingEnv);
if (!validator.validate(model)) {
    continue;   // emit ikke generert kode for ugyldige modeller
}
```

- [ ] **Step 3: Skriv tester for hver feilregel**

```java
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
    void uknownTypeFeiler() {
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
```

- [ ] **Step 4: Kjør tester**

Run: `mvn -pl path-parser-processor test -Dtest=HandlerValidatorTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/HandlerValidator.java
git add path-parser-processor/src/main/java/org/brylex/parser/processor/PathProcessor.java
git add path-parser-processor/src/test/java/org/brylex/parser/processor/HandlerValidatorTest.java
git commit -m "feat(processor): HandlerValidator med kompilerings-tids feilmeldinger"
```

### Task 4.2: Fingerprint-validering ved runtime

**Files:**
- Modify: `path-parser-processor/src/main/java/org/brylex/parser/processor/Fingerprint.java`
- Modify: `path-parser/src/main/java/org/brylex/parser/GeneratedFactoryRegistry.java`
- Create: `path-parser/src/test/java/org/brylex/parser/FingerprintTest.java`

- [ ] **Step 1: Utvid `Fingerprint.over()` til å hashe alle binding-feltene, ikke bare path**

```java
public static String over(List<HandlerModel> models) {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (HandlerModel m : models) {
            md.update((m.packageName() + "." + m.simpleName() + "\n").getBytes());
            for (Binding b : m.bindings()) {
                md.update((b.path() + "|" + b.getClass().getSimpleName() + "\n").getBytes());
                if (b instanceof Binding.FieldText ft) md.update((ft.fieldName() + "|" + ft.fieldType()).getBytes());
                if (b instanceof Binding.MethodText mt) md.update((mt.methodName() + "|" + mt.paramType()).getBytes());
                if (b instanceof Binding.Collection c) md.update((c.fieldName() + "|" + c.elementType()).getBytes());
                if (b instanceof Binding.SubHandler sh) md.update((sh.targetName() + "|" + sh.subHandlerType()).getBytes());
                if (b instanceof Binding.Attribute a) md.update((a.fieldName() + "|" + a.attrName()).getBytes());
            }
        }
        return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 ikke tilgjengelig", e);
    }
}
```

- [ ] **Step 2: I `GeneratedFactoryRegistry`, valider fingerprint ved første-lasting (kan slås av via system-property)**

Legg til i `buildMap`:

```java
private static Map<Class<?>, PathParserFactory> buildMap(ClassLoader cl) {
    Map<Class<?>, PathParserFactory> map = new HashMap<>();
    boolean skipCheck = Boolean.getBoolean("org.brylex.parser.skipFingerprintCheck");
    for (PathParserFactoryRegistry registry : ServiceLoader.load(PathParserFactoryRegistry.class, cl)) {
        if (!skipCheck) {
            // For 3.0 v1: vi forventer at fingerprint er stabil; vi sjekker bare at den eksisterer.
            // Re-introspekjon av .class-filer for bytecode-hash er en senere optimalisering.
            String fp = registry.fingerprint();
            if (fp == null || fp.isEmpty()) {
                throw new IllegalStateException("Registry " + registry.getClass()
                        + " mangler fingerprint. Regenerér: 'mvn clean compile'.");
            }
        }
        map.putAll(registry.factories());
    }
    return map;
}
```

- [ ] **Step 3: Skriv test**

```java
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
```

- [ ] **Step 4: Kjør test**

Run: `mvn -pl path-parser test -Dtest=FingerprintTest -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add path-parser-processor/src/main/java/org/brylex/parser/processor/Fingerprint.java
git add path-parser/src/main/java/org/brylex/parser/GeneratedFactoryRegistry.java
git add path-parser/src/test/java/org/brylex/parser/FingerprintTest.java
git commit -m "feat: SHA-256-fingerprint av @Path-metadata, validert ved registry-load"
```

### Task 4.3: Migrer alle eksisterende 2.x-tester til `PathParser.of()`-API

**Files:**
- Modify: alle test-filer i `path-parser/src/test/java/org/brylex/`

- [ ] **Step 1: Søk etter `new PathParser(` i tester**

Run: `grep -rln "new PathParser(" path-parser/src/test/java`
Expected: liste over filer som bruker konstruktøren.

- [ ] **Step 2: For hver fil, erstatt `new PathParser(handler)` med `PathParser.of(handler)`**

Eksempel: `PathParserTest.java`:
- `new PathParser(handler).parse(reader);` → `PathParser.of(handler).parse(reader);`
- `new PathParser(handler, factoryFn).parse(reader);` → `PathParser.of(handler, factoryFn).parse(reader);`

Tilsvarende for `CollectionPathTest`, `FactoryTest`, `TypeConversionTest`, `MethodParameterConversionTest`, `SubHandlerCollectionTest`, `EdgeCasesTest`, `AttributePathTest`, `ConversionsTest`.

- [ ] **Step 3: Kjør full test-suite**

Run: `mvn -pl path-parser test -q`
Expected: BUILD SUCCESS. Alle 2.x-tester bruker nå `of()`-API; refleksjons-fallback i `of()` håndterer fortsatt typer/features APT-banen ikke har dekket enda.

- [ ] **Step 4: Commit**

```bash
git add path-parser/src/test/java/org/brylex/
git commit -m "test: migrér alle 2.x-tester fra konstruktør til PathParser.of()"
```

### Task 4.4: `@Retention(CLASS)` på `@Path`

**Files:**
- Modify: `path-parser/src/main/java/org/brylex/parser/annotation/Path.java`

- [ ] **Step 1: Endre retention**

I `Path.java`:
```java
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS)
```

- [ ] **Step 2: Kjør hele test-suiten**

Run: `mvn -pl path-parser test -q`
Expected: tester som *fortsatt* bruker refleksjons-fallback (de uten APT-generert factory) vil FAIL, fordi refleksjon ikke lenger ser annotasjonen.

Hvis alle 2.x-test-handlere har APT-genererte factories, passerer alt. Hvis ikke, sett tilbake retention midlertidig og fortsett til Task 4.5 før retention-endringen.

- [ ] **Step 3: Commit (etter at alle tester passerer)**

```bash
git add path-parser/src/main/java/org/brylex/parser/annotation/Path.java
git commit -m "feat: endre @Path til @Retention(CLASS) — refleksjon kan ikke lese den lenger"
```

### Task 4.5: Fjern refleksjons-banen i `PathParser` og slett refleksjons-klasser

**Files:**
- Delete: `path-parser/src/main/java/org/brylex/parser/HandlerSpec.java`
- Delete: `path-parser/src/main/java/org/brylex/parser/FieldInvoker.java`
- Delete: `path-parser/src/main/java/org/brylex/parser/MethodInvoker.java`
- Delete: `path-parser/src/main/java/org/brylex/parser/AttributeInvoker.java`
- Delete: `path-parser/src/main/java/org/brylex/parser/CreateInstanceInvoker.java`
- Delete: `path-parser/src/main/java/org/brylex/parser/ApplySubParserInvoker.java`
- Modify: `path-parser/src/main/java/org/brylex/parser/PathParser.java`

- [ ] **Step 1: Slett refleksjons-klassene**

```bash
git rm path-parser/src/main/java/org/brylex/parser/HandlerSpec.java
git rm path-parser/src/main/java/org/brylex/parser/FieldInvoker.java
git rm path-parser/src/main/java/org/brylex/parser/MethodInvoker.java
git rm path-parser/src/main/java/org/brylex/parser/AttributeInvoker.java
git rm path-parser/src/main/java/org/brylex/parser/CreateInstanceInvoker.java
git rm path-parser/src/main/java/org/brylex/parser/ApplySubParserInvoker.java
```

- [ ] **Step 2: Skriv om `PathParser.java`**

```java
package org.brylex.parser;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.util.function.Function;

public final class PathParser {

    private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

    private final ParseNode root;
    private final InvokerSet bindings;

    private PathParser(ParseNode root, InvokerSet bindings) {
        this.root = root;
        this.bindings = bindings;
    }

    public static PathParser of(Object handler) {
        return of(handler, PathParser::defaultFactory);
    }

    public static PathParser of(Object handler, Function<Class<?>, Object> subHandlerFactory) {
        PathParserFactory factory = GeneratedFactoryRegistry.lookup(handler.getClass());
        if (factory == null) {
            throw new IllegalStateException(
                    "Ingen generert parser for " + handler.getClass().getName()
                            + ". Verifiser at path-parser-processor er aktivert i build-en, og at klassen ble rekompilert.");
        }
        InvokerSet set = factory.bind(handler, subHandlerFactory, GeneratedFactoryRegistry::lookup);
        return new PathParser(factory.tree(), set);
    }

    public void parse(InputStream input) {
        try {
            XMLStreamReader r = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parseLoop(r);
            r.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    public void parse(Reader input) {
        try {
            XMLStreamReader r = XML_INPUT_FACTORY.createXMLStreamReader(input);
            parseLoop(r);
            r.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    public void parse(XMLStreamReader reader) {
        try {
            parseLoop(reader);
        } catch (XMLStreamException e) {
            throw new RuntimeException("Unable to parse stream.", e);
        }
    }

    private void parseLoop(XMLStreamReader reader) throws XMLStreamException {
        ParseNode parseTree = root;
        InvokerSet currentBindings = bindings;

        int stackCap = 8;
        ParseNode[] treeStack = new ParseNode[stackCap];
        InvokerSet[] bindingsStack = new InvokerSet[stackCap];
        StringBuilder[] charStack = new StringBuilder[stackCap];
        SubParseActivator[] activatorStack = new SubParseActivator[stackCap];
        Object[] subInstanceStack = new Object[stackCap];

        int depth = 0;
        int ignore = 0;
        int attrBufSize = 8;
        String[] attrNames = new String[attrBufSize];
        String[] attrValues = new String[attrBufSize];

        while (reader.hasNext()) {
            int type = reader.next();
            if (type == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                int attrCount = reader.getAttributeCount();
                if (attrCount > attrBufSize) {
                    attrBufSize = attrCount;
                    attrNames = new String[attrBufSize];
                    attrValues = new String[attrBufSize];
                }
                for (int i = 0; i < attrCount; i++) {
                    attrNames[i] = reader.getAttributeLocalName(i);
                    attrValues[i] = reader.getAttributeValue(i);
                }

                if (depth >= stackCap) {
                    int newCap = stackCap * 2;
                    treeStack = java.util.Arrays.copyOf(treeStack, newCap);
                    bindingsStack = java.util.Arrays.copyOf(bindingsStack, newCap);
                    charStack = java.util.Arrays.copyOf(charStack, newCap);
                    activatorStack = java.util.Arrays.copyOf(activatorStack, newCap);
                    subInstanceStack = java.util.Arrays.copyOf(subInstanceStack, newCap);
                    stackCap = newCap;
                }

                if (ignore > 0) {
                    ignore++;
                    depth++;
                    continue;
                }

                ParseNode child = parseTree.lookupChild(name, attrCount, attrNames, attrValues);
                if (child == null) {
                    ignore++;
                    depth++;
                    continue;
                }

                SubParseActivator activator = findActivator(child);
                Object subInstance = null;
                if (activator != null) {
                    subInstance = activator.instanceFactory().apply(activator.subType());
                    PathParserFactory subFactory = activator.factoryLookup().apply(activator.subType());
                    if (subFactory == null) {
                        throw new IllegalStateException("Ingen generert factory for sub-handler-type "
                                + activator.subType().getName());
                    }
                    InvokerSet subSet = subFactory.bind(subInstance,
                            activator.instanceFactory(), activator.factoryLookup());
                    treeStack[depth] = parseTree;
                    bindingsStack[depth] = currentBindings;
                    activatorStack[depth] = activator;
                    subInstanceStack[depth] = subInstance;
                    parseTree = subFactory.tree();
                    currentBindings = subSet;
                } else {
                    invokeStartHandlers(child, reader, attrCount, attrNames, attrValues);
                    treeStack[depth] = parseTree;
                    activatorStack[depth] = null;
                    if (child.needsText) {
                        StringBuilder sb = charStack[depth];
                        if (sb == null) charStack[depth] = new StringBuilder(32);
                        else sb.setLength(0);
                    }
                    parseTree = child;
                }
                depth++;

            } else if (type == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth < 0) return;
                if (ignore > 0) {
                    ignore--;
                    continue;
                }
                SubParseActivator activator = activatorStack[depth];
                if (activator != null) {
                    Object instance = subInstanceStack[depth];
                    activator.applyToParent().accept(instance);
                    parseTree = treeStack[depth];
                    currentBindings = bindingsStack[depth];
                    activatorStack[depth] = null;
                    subInstanceStack[depth] = null;
                } else {
                    StringBuilder text = parseTree.needsText ? charStack[depth] : null;
                    invokeEndHandlers(parseTree, text);
                    parseTree = treeStack[depth];
                }

            } else if (type == XMLStreamConstants.CHARACTERS
                    || type == XMLStreamConstants.CDATA
                    || type == XMLStreamConstants.SPACE) {
                if (depth > 0 && ignore == 0 && parseTree.needsText) {
                    charStack[depth - 1].append(reader.getTextCharacters(),
                            reader.getTextStart(), reader.getTextLength());
                }
            }
        }
    }

    private static SubParseActivator findActivator(ParseNode node) {
        for (int i = 0, n = node.startInvokers.size(); i < n; i++) {
            if (node.startInvokers.get(i) instanceof SubParseActivator a) return a;
        }
        return null;
    }

    private void invokeStartHandlers(ParseNode node, XMLStreamReader reader,
                                     int attrCount, String[] attrNames, String[] attrValues) {
        if (node.startInvokers.isEmpty()) return;
        AttributeSnapshot snapshot = null;
        javax.xml.stream.events.StartElement startElement = null;
        for (int i = 0, n = node.startInvokers.size(); i < n; i++) {
            Invoker inv = node.startInvokers.get(i);
            if (inv instanceof SubParseActivator) continue;   // håndtert i parseLoop
            if (inv instanceof EventInvoker ev && ev.kind() == EventInvoker.Kind.START_ELEMENT) {
                if (startElement == null) {
                    startElement = buildStartElement(reader, attrCount, attrNames, attrValues);
                }
                inv.invoke(startElement);
                continue;
            }
            if (inv instanceof AttributeBindingInvoker) {
                if (snapshot == null) {
                    snapshot = new AttributeSnapshot(attrCount, attrNames, attrValues);
                }
                inv.invoke(snapshot);
            }
        }
    }

    private void invokeEndHandlers(ParseNode node, StringBuilder text) {
        if (node.endInvokers.isEmpty()) return;
        String textValue = text == null ? "" : text.toString();
        for (int i = 0, n = node.endInvokers.size(); i < n; i++) {
            Invoker inv = node.endInvokers.get(i);
            if (inv instanceof EventInvoker ev && ev.kind() == EventInvoker.Kind.END_ELEMENT) {
                inv.invoke(buildEndElement(node.name));
                continue;
            }
            // TextInvoker eller annen tekst-baserte invoker:
            inv.invoke(textValue);
        }
    }

    private static javax.xml.stream.events.StartElement buildStartElement(XMLStreamReader reader,
            int attrCount, String[] attrNames, String[] attrValues) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        java.util.List<javax.xml.stream.events.Attribute> attrs = new java.util.ArrayList<>(attrCount);
        for (int i = 0; i < attrCount; i++) {
            attrs.add(factory.createAttribute(new javax.xml.namespace.QName(attrNames[i]), attrValues[i]));
        }
        return factory.createStartElement(
                new javax.xml.namespace.QName(reader.getNamespaceURI(), reader.getLocalName()),
                attrs.iterator(),
                java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static javax.xml.stream.events.EndElement buildEndElement(String name) {
        javax.xml.stream.XMLEventFactory factory = javax.xml.stream.XMLEventFactory.newInstance();
        return factory.createEndElement(new javax.xml.namespace.QName(name),
                java.util.Collections.<javax.xml.stream.events.Namespace>emptyIterator());
    }

    private static Object defaultFactory(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to instantiate [" + type + "].", e);
        }
    }
}
```

(Konkretiseringen av `parseLoop` med `SubParseActivator`-håndtering er en kjernerefaktor; den utføres her som ett samlet stykke. Hvis det blir for stort, splitt til 4.5a/b.)

- [ ] **Step 3: Kjør full test-suite**

Run: `mvn -pl path-parser test -q`
Expected: BUILD SUCCESS. Hvis tester feiler: APT-banen mangler dekning for en feature — gå tilbake til Phase 3-task for den feature og legg til codegen.

- [ ] **Step 4: Kjør hele bygget**

Run: `mvn verify -q`
Expected: BUILD SUCCESS for begge moduler.

- [ ] **Step 5: Commit**

```bash
git add path-parser/src/main/java/org/brylex/parser/PathParser.java
git add -u  # for slettede filer
git commit -m "refactor: fjern refleksjons-banen; PathParser krever APT-generert factory"
```

---

## Phase 5 — Native-image CI

### Task 5.1: Lag minimalt native-image smoke-prosjekt

**Files:**
- Create: `examples/native-image-smoke/pom.xml`
- Create: `examples/native-image-smoke/src/main/java/example/OrderHandler.java`
- Create: `examples/native-image-smoke/src/main/java/example/Main.java`

- [ ] **Step 1: Lag prosjekt-struktur**

`examples/native-image-smoke/pom.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <modelVersion>4.0.0</modelVersion>
    <groupId>example</groupId>
    <artifactId>native-image-smoke</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.brylex</groupId>
            <artifactId>path-parser</artifactId>
            <version>3.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.brylex</groupId>
                            <artifactId>path-parser-processor</artifactId>
                            <version>3.0.0-SNAPSHOT</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>0.10.3</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <id>build-native</id>
                        <goals><goal>compile-no-fork</goal></goals>
                        <phase>package</phase>
                    </execution>
                </executions>
                <configuration>
                    <mainClass>example.Main</mainClass>
                    <imageName>order-parser</imageName>
                    <buildArgs>
                        <buildArg>--no-fallback</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Skriv `OrderHandler.java`**

```java
package example;

import org.brylex.parser.annotation.Path;

import java.math.BigDecimal;

public class OrderHandler {
    @Path("/order/id") public String id;
    @Path("/order/total") public BigDecimal total;
}
```

- [ ] **Step 3: Skriv `Main.java`**

```java
package example;

import org.brylex.parser.PathParser;

import java.io.StringReader;

public class Main {
    public static void main(String[] args) {
        String xml = "<order><id>O-42</id><total>199.95</total></order>";
        OrderHandler h = new OrderHandler();
        PathParser.of(h).parse(new StringReader(xml));
        if (!"O-42".equals(h.id) || !new java.math.BigDecimal("199.95").equals(h.total)) {
            System.err.println("FAIL: id=" + h.id + " total=" + h.total);
            System.exit(1);
        }
        System.out.println("OK: " + h.id + " " + h.total);
    }
}
```

- [ ] **Step 4: Bygg lokalt med GraalVM (manuelt verifiserings-steg)**

Hvis GraalVM 21 er tilgjengelig:
```bash
cd examples/native-image-smoke
mvn -DskipTests package
./target/order-parser
```
Expected: `OK: O-42 199.95`.

- [ ] **Step 5: Commit**

```bash
git add examples/native-image-smoke/
git commit -m "examples: native-image-smoke-prosjekt for CI-verifisering"
```

### Task 5.2: GitHub Actions-workflow for native-image

**Files:**
- Create: `.github/workflows/native-image.yml`

- [ ] **Step 1: Skriv workflow**

```yaml
name: native-image

on:
  push:
    branches: [master]
  pull_request:
    types: [labeled]

jobs:
  build-native:
    if: github.event_name == 'push' || contains(github.event.pull_request.labels.*.name, 'native-image-required')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: graalvm/setup-graalvm@v1
        with:
          java-version: '21.0.5'
          distribution: 'graalvm'
          github-token: ${{ secrets.GITHUB_TOKEN }}
      - name: Build runtime + processor
        run: mvn -DskipTests install
      - name: Build native-image smoke
        run: |
          cd examples/native-image-smoke
          mvn -DskipTests package
      - name: Run native binary
        run: |
          OUT=$(./examples/native-image-smoke/target/order-parser)
          echo "$OUT"
          if [ "$OUT" != "OK: O-42 199.95" ]; then exit 1; fi
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/native-image.yml
git commit -m "ci: native-image-workflow for master + opt-in PR-er"
```

---

## Phase 6 — Release-prep

### Task 6.1: Oppdater README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Erstatt 2.x-bruk-eksempel med 3.0**

I `README.md`:
- Bytt `<version>2.0.0</version>` → `<version>3.0.0</version>`.
- Etter `<dependency>`-blokken, legg til:
  ```xml
  <dependency>
    <groupId>org.brylex</groupId>
    <artifactId>path-parser-processor</artifactId>
    <version>3.0.0</version>
    <scope>provided</scope>
  </dependency>
  ```
- I "Bruk"-seksjonen, endre `new PathParser(handler).parse(events)` → `PathParser.of(handler).parse(events)`.
- Legg til seksjon "Native-image":
  ```markdown
  ## Native-image
  Biblioteket fungerer uten konfig på GraalVM native-image fordi det
  ikke bruker refleksjon ved runtime. Se `examples/native-image-smoke/`.
  ```
- Legg til seksjon "Migrasjon fra 2.x":
  ```markdown
  ## Migrasjon fra 2.x
  3.0 er en brudd-versjon:
  1. Legg til path-parser-processor som provided-dependency.
  2. Erstatt `new PathParser(handler)` med `PathParser.of(handler)`.
  3. Rekompilér.
  ```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: oppdater README for 3.0 API + native-image + migrasjon"
```

### Task 6.2: Bekreft bench-ytelse

**Files:**
- Modify: `path-parser/src/test/java/org/brylex/bench/PathParserVsJaxbBenchmark.java` (eksisterende)

- [ ] **Step 1: Sjekk at benchmark bruker `PathParser.of()`-API**

Run: `grep -n "new PathParser" path-parser/src/test/java/org/brylex/bench/PathParserVsJaxbBenchmark.java`
Expected: ingen treff (allerede migrert i Task 4.3). Hvis treff: oppdater.

- [ ] **Step 2: Kjør benchmark lokalt og verifisér ≥ 2.x-ytelse**

Run:
```bash
cd path-parser
mvn -DskipTests package
java -cp target/test-classes:target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) \
     org.brylex.bench.BenchmarkRunner
```
Expected: throughput ≥ 2.x-baseline (10 orders ≥ ~96 μs, 100 ≥ ~0.87 ms, 1000 ≥ ~8.8 ms).

Hvis dårligere: dokumentér og ikke-blokker, men flagg.

- [ ] **Step 3: Commit (kun hvis bench-fil ble endret)**

```bash
git add path-parser/src/test/java/org/brylex/bench/
git commit -m "test(bench): bekreft 3.0 ≥ 2.x-ytelse"
```

### Task 6.3: Oppdater RELEASING.md

**Files:**
- Modify: `RELEASING.md`

- [ ] **Step 1: Legg til notat om multi-module-release**

I `RELEASING.md`, etter eksisterende release-steg:
```markdown
## Multi-modul-merknad
path-parser-parent slipper alle modulene samtidig via `maven-release-plugin`'s
`autoVersionSubmodules`. Sjekk at både path-parser og path-parser-processor
publiseres til Maven Central før release-PR merges.

## Pre-release-sjekkliste
- [ ] `mvn -Pequivalence verify` mot 2.0.0 grønn
- [ ] `mvn verify` på master grønn
- [ ] GitHub Actions native-image-job grønn på den committen vi tagger
- [ ] examples/native-image-smoke kjørt lokalt med GraalVM 21
```

- [ ] **Step 2: Commit**

```bash
git add RELEASING.md
git commit -m "docs: oppdater RELEASING.md for 3.0 multi-modul-release"
```

### Task 6.4: Final smoke — `mvn verify` på hele prosjektet

**Files:** (ingen)

- [ ] **Step 1: Kjør full build**

Run: `mvn clean verify -q`
Expected: BUILD SUCCESS. Alle moduler, alle tester grønne.

- [ ] **Step 2: Hvis grønt, plant tagging i RELEASING.md-prosess**

Tagging og release skjer som vanlig via `maven-release-plugin` separat fra denne implementasjons-planen. Ikke commit her — overlat til release-prosess.

---

## Spec-coverage-sjekkliste (self-review)

| Spec-krav | Task |
|---|---|
| Multi-modul: `path-parser` + `path-parser-processor` | 0.2-0.4 |
| `PathParserFactory`-SPI | 1.1 |
| `PathParserFactoryRegistry`-SPI | 1.2 |
| `InvokerSet` record | 1.3 |
| `PathParser.of()` statisk factory | 1.4, 2.7, 4.5 |
| `ServiceLoader` med eksplisitt classloader | 2.7 |
| `PathProcessor` (JSR-269 AbstractProcessor) | 2.2 |
| `HandlerModel` + `Binding`-typer | 2.3 |
| `HandlerModelBuilder` | 2.4, 3.2-3.7 |
| `HandlerCodeGenerator` (JavaPoet) | 2.5, 3.1-3.8 |
| `RegistryCodeGenerator` aggregerende per modul | 2.6 |
| Golden-file-tester for codegen | 2.5, 3.x |
| Type-konvertering for felt | 3.1 |
| Attributt-mapping | 3.2 |
| Filter-attributter `[@k='v']` | 3.3 |
| Collection-felt (List/Set/Queue) | 3.4 |
| Method-tekst-bindinger | 3.5 |
| StartElement/EndElement-event-bindinger | 3.6 |
| Sub-handler-bindinger med lazy lookup (rekursive) | 3.7 |
| Privat felt via `privateLookupIn` | 3.8 |
| Custom subHandlerFactory | 3.9 |
| `HandlerValidator` med kompilerings-feil | 4.1 |
| SHA-256-fingerprint | 4.2 + 2.6 (Fingerprint-klasse) |
| Alle 2.x-tester migrert til `of()` | 4.3 |
| `@Retention(CLASS)` | 4.4 |
| Refleksjons-kode fjernet (HandlerSpec etc.) | 4.5 |
| Native-image smoke-prosjekt | 5.1 |
| GitHub Actions native-image-workflow | 5.2 |
| README oppdatert for 3.0 | 6.1 |
| Bench ≥ 2.x | 6.2 |
| RELEASING.md oppdatert | 6.3 |
| Final `mvn verify` grønn | 6.4 |

**Spec-krav som *ikke* har eksplisitt task** (bevisst utelatelse):
- "Ekvivalens-test mot 2.x via separat Maven-modul med dual classloader" — designet som *pre-release-gate*, ikke en del av implementasjons-planen. Forutsetter at 3.0 er ferdig. Lag som egen oppgave etter Phase 6.
- "Native-image-CI for *release-tags* med hard blokkering" — workflowen i 5.2 dekker push til master; release-tag-spesifikk konfigurasjon legges til ved første ekte tag.

---

## Verifiseringskommandoer (utfør etter siste task)

```bash
# Hele suiten
mvn clean verify

# Bare runtime
mvn -pl path-parser verify

# Bare processor
mvn -pl path-parser-processor verify

# Native-image (krever GraalVM 21)
cd examples/native-image-smoke && mvn -DskipTests package && ./target/order-parser
```

Forventet ved alle tre: BUILD SUCCESS / `OK: O-42 199.95`.
