# path-parser 3.0 — kompilerings-tids `@Path` annotation processor

Status: Forslag • Dato: 2026-05-19 • Branch: master

## Sammendrag

path-parser 3.0 fjerner all refleksjon ved runtime og erstatter den med en JSR-269 annotation processor som genererer én `*_PathParser`-klasse per `@Path`-annotert handler ved kompilering. Generert kode inneholder hele `ParseNode`-treet som statisk konstant og direkte felt-/metode-invokers — ingen `Field.set`, ingen `setAccessible`, ingen `Class.forName`. Runtime laster generert kode via `ServiceLoader`. Mål: native-image-kompatibilitet uten brukerkonfig, redusert oppstart-kostnad, og 1.2-1.5× raskere parsing enn 2.x på warm JVM.

## Bakgrunn og motivasjon

2.x bruker refleksjon: `HandlerSpec.compile(Class)` bygger ett `ParseNode`-tre per handler-klasse ved første-bruk via `Field.getDeclaredFields` og `setAccessible(true)`. Det fungerer godt på JVM, men:

- Refleksjon er på vei ut som førsteklasses primitiv. `setAccessible` krever økende `--add-opens`. Project Leyden, Valhalla, og GraalVM beveger seg mot statisk metadata.
- GraalVM native-image krever per-klasse `reflect-config.json` — biblioteket fungerer ikke ut-av-boksen på native-image i dag.
- Første-bruk har målbar latency for store handler-trær (relevant i serverless / cold-start).

Med statisk kodegenerering blir biblioteket reflection-free, native-image-klart uten konfig, og posisjonerer seg som JAXB-erstatter for moderne Java.

## Designvalg (fra brainstorming)

1. **Hard erstatning i 3.0.** Ingen refleksjons-fallback. Én kodebane, brudd-versjon.
2. **Tre + invokers som generert data.** Hele `ParseNode`-treet emitteres som statisk konstant; invokers er genererte klasser/lambdaer som kaller felt-set direkte.
3. **Statisk factory + `ServiceLoader`.** Brukere kaller `PathParser.of(handler).parse(reader)`. Generert klasse registreres i `META-INF/services/org.brylex.parser.PathParserFactory`. Ingen `Class.forName`-streng-magi.
4. **GraalVM native-image er førsteklasses, non-negotiable.** CI bygger og kjører test-suite under `native-image`. Brudd blokkerer release.
5. **To artefakter.** `path-parser` (runtime + annotations) + `path-parser-processor` (APT, `<scope>provided</scope>`). Idiomatisk Lombok/MapStruct-mønster.

## Forkastede alternativer

- **ByteBuddy/ASM runtime-codegen** — kolliderer med native-image-målet og legger ~3 MB dependency i runtime-JAR.
- **Hybrid opt-in (refleksjons-fallback alongside APT)** — to permanente kodebaner gir teknisk gjeld uten klar slutt-tilstand.
- **Generert state-maskin uten `ParseNode`-tre** — marginal ekstra ytelse, kompleks generert kildekode, høyere vedlikeholds-kostnad.

## Arkitektur

```
Bruker-modul (compile-time)
  OrderHandler.java med @Path-annotasjoner
  pom.xml: dep path-parser + dep path-parser-processor (provided)
            │
            ▼ javac kjører PathProcessor
target/generated-sources/annotations/
  OrderHandler_PathParser.java
    implements PathParserFactory
    static final ParseNode TREE
    InvokerSet bind(Object handler, Function<Class<?>,Object> sub)
  META-INF/services/org.brylex.parser.PathParserFactory
    append: pkg.OrderHandler_PathParser

Runtime
  PathParser.of(handler)
    → ServiceLoader.load(PathParserFactory.class) (cachet i ClassValue)
    → factory.bind(handler, subFactory) → InvokerSet
    → returnerer PathParser med ferdig tre + bound invokers
  .parse(reader)
    → uendret cursor/parseLoop, jobber mot generert tre
```

**Kjerneprinsipp:** Tre-strukturen er statiske, immutable data delt mellom parser-instanser. `InvokerSet` er per-instans (bærer handler-referansen). `parseLoop` i runtime-modulen er uendret — den ser bare `Invoker`-interfacet og vet ikke om implementasjonen er generert eller refleksjons-basert.

## Komponenter

### Compile-time (`path-parser-processor`)

**`PathProcessor extends AbstractProcessor`** — entry-point. Lytter på `@Path` (retention endres fra `RUNTIME` til `CLASS`). Per `RoundEnvironment`: samler kandidatklasser via `getElementsAnnotatedWith`. For hver: bygger `HandlerModel`, validerer, genererer, skriver via `Filer`.

**`HandlerModel`** — speilbilde av dagens `HandlerSpec`, men bygget fra `javax.lang.model.element` / `TypeMirror` (kompilator-API). `List<Binding>` med subtypene:
- `FieldBinding` — felt-tilordning med type-konvertering
- `MethodTextBinding` — metode-kall med tekstinnhold som argument
- `MethodSubHandlerBinding` — metode-kall med ferdig populert sub-handler
- `MethodEventBinding` — metode med `StartElement`/`EndElement`-parameter
- `AttributeBinding` — `@`-prefiks-tilordning
- `CollectionBinding` — collect-into-`List`/`Set`/`Queue`

Vet om type-konvertering, element-type i collection, filter-attributter, sub-handler-rekursjon.

**`HandlerValidator`** — kjører på `HandlerModel`. Feiler kompilering med `Diagnostic.Kind.ERROR` ved:
- Duplikate `@Path`-uttrykk innen samme klasse
- Type som ikke kan konverteres og ikke er en sub-handler-kandidat
- Malformerte path-uttrykk
- Sub-handler-typer uten egne `@Path`-elementer
- Selv-refererende sub-handler-grafer
- Tom `@Path`-verdi

**`HandlerCodeGenerator`** — bygger Java-kilde via JavaPoet. Emitterer én `<HandlerName>_PathParser`-klasse i samme pakke som handleren. Genererer:
- Statisk `ParseNode TREE` som klasse-initializer
- `bind(handler, subFactory)` med direkte felt-/metode-referanser

### Runtime (`path-parser`)

**`PathParserFactory`** — sealed SPI:
```java
public sealed interface PathParserFactory {
    Class<?> handlerType();
    ParseNode tree();
    InvokerSet bind(Object handler, Function<Class<?>, Object> subFactory);
}
```

**`InvokerSet`** — record som holder handler-instans og dens bound invokers. Brukes av `parseLoop` som i dag.

**`PathParser`** — beholder `parseLoop`-implementasjonen uendret. Konstruktørene fjernes; erstattes av:
```java
public static PathParser of(Object handler);
public static PathParser of(Object handler, Function<Class<?>, Object> subHandlerFactory);
```

Begge gjør `ServiceLoader.load(PathParserFactory.class)` → cache → match på `handlerType()` → bind → returner ferdig `PathParser`. Cache holdes i `ClassValue<PathParserFactory>`.

**`@Path`** — endres fra `@Retention(RUNTIME)` til `@Retention(CLASS)`. Sparer minne ved runtime og signaliserer at den ikke brukes til runtime-introspeksjon.

### Sub-handlere

Hver sub-handler-type (f.eks. `Item` brukt av `OrderHandler.onItem(Item)`) får sin egen `Item_PathParser`. Generert `OrderHandler_PathParser` slår opp `Item_PathParser` via `ServiceLoader`-cache ved `bind()`-tid (én gang per parser-instans), ikke per element-match.

### Privat felt-tilgang

Generert `<H>_PathParser` ligger i samme pakke som `<H>` (`Filer` skriver dit).
- `public`/`package-private` felt: direkte `handler.id = value`. Ingen refleksjon.
- `private` felt: APT genererer `private static final VarHandle ID = MethodHandles.lookup().findVarHandle(...)` i samme pakke (har dermed pakke-tilgang via `lookup()`). `VarHandle.set` brukes. `VarHandle` er førsteklasses native på GraalVM.
- `private`-metoder: tilsvarende `MethodHandle` via `lookup.findVirtual(...)`.

Beholder feature-paritet med 2.x uten å påkreve at felt eksponeres.

## Datafly

### Compile-time

```
@Path-annoterte klasser
  → PathProcessor.process(round)
  → For hver kandidat:
     1. Bygg HandlerModel fra Elements/TypeMirrors
     2. HandlerValidator.validate(model)
        feil → Messager.printMessage(ERROR) → kompilering feiler
     3. HandlerCodeGenerator.emit(model) → JavaFileObject
     4. Filer.createSourceFile("<pkg>.<H>_PathParser")
     5. Filer.createResource("META-INF/services/...PathParserFactory")
        append FQN av generert klasse
```

### Runtime — `PathParser.of(handler)`

```
PathParser.of(orderHandler)
  → ServiceLoader.load(PathParserFactory.class)
  → Bygg én gang Map<Class<?>,PathParserFactory> (lazy i ClassValue)
  → factory = map.get(orderHandler.getClass())
    null → IllegalStateException med actionable melding
  → InvokerSet set = factory.bind(orderHandler, subFactory)
     bind() kobler hver Binding til handler-instansen.
     Sub-handler-bindinger henter Item_PathParser fra samme cache.
  → return new PathParser(factory.tree(), set)
```

### Runtime — `parse(reader)`

Uendret fra dagens `parseLoop`. `ParseNode.startInvokers`/`endInvokers` har nå genererte instanser i stedet for refleksjons-baserte `FieldInvoker`/`MethodInvoker`, men `Invoker.invoke(...)`-interfacet er uendret.

### Sub-handler-instansiering

```
<order><item> oppdaget
  → startInvoker = SubHandlerStart
  → Item itemInstance = (Item) subFactory.apply(Item.class)
     standard: generert "new Item()" via direkte konstruktør-kall
     custom factory: brukerens Function kalles
  → push Item_PathParser-tree på stack, bind itemInstance
  → Item-feltene fylles inntil </item>
  → </item> → ApplySubParser.fire()
     → direkte kall til OrderHandler.onItem(itemInstance) eller field-add
```

## Feilhåndtering

### Compile-time-feilmeldinger

Eksempel-meldinger (alle via `Messager.printMessage(ERROR, msg, element)` for IDE-integrasjon):

| Feil | Melding |
|---|---|
| Duplikat `@Path` | `"@Path('/order/id') deklareres to ganger i OrderHandler: feltet 'id' og metoden 'setId'."` |
| Ikke-konvertibel type | `"Felt-type 'java.net.URL' kan ikke konverteres fra tekst, og inneholder ingen @Path-annoterte elementer for sub-handler-bruk."` |
| Malformert path | `"Path '/order/item[@id=' har malformert filter-uttrykk."` |
| Sykler | `"OrderHandler refererer seg selv via @Path-feltet 'parent'; sykliske sub-handlere er ikke støttet."` |
| Tom path | `"@Path-verdien kan ikke være tom."` |

### Runtime-feil i `PathParser.of`

| Feil | Atferd |
|---|---|
| Ingen factory for handler-klassen | `IllegalStateException("Ingen generert parser for X. Verifiser at path-parser-processor er aktivert i build-en, og at klassen ble rekompilert.")` |
| `ServiceLoader` finner ingen tjenester | Samme exception med ekstra hint om `META-INF/services`-stien |
| Sub-handler-type mangler factory | Fanges ved `bind()`-tid med peker til parent-handler — ikke under parsing |

**Filosofi:** Fail-fast. Alt valideres ved `PathParser.of(...)`. Ingen mystiske NPE-er under parsing.

### Parsing-tid

Type-konverteringsfeil (`NumberFormatException`), I/O-feil fra `XMLStreamReader`, manglende `factory`-resultat for sub-handlere propagerer som i 2.x. Ingen ny semantikk.

### Edge-case: stale generert kode

Bruker endrer `@Path`-streng, glemmer å rekompilere. Detekteres ikke automatisk — VarHandle/felt-set kjører videre med gammel path. Samme problem som alle APT-baserte bibliotek (MapStruct, Dagger). Mitigering: inkrementell kompilering i moderne build-verktøy. Dokumentert i README-feilsøkings-seksjon.

## Testing

### Compile-time-tester (i processor-modul)

**`PathProcessorTest`** — bruker `google-compile-testing` eller `java.compiler`-API direkte:
- Per `HandlerValidator`-regel: en test som mater en handler-stub, kjører APT, asserter `Diagnostic.Kind.ERROR` med riktig melding på riktig linje.
- Happy path: kompiler handler, asserter at `<H>_PathParser.java` og `META-INF/services/...`-fil ble generert med rett innhold.

**Golden-file-tester for `HandlerCodeGenerator`** — `src/test/resources/golden/`:
- Per feature-kombinasjon: én `Input.java` + én `Input_PathParser.golden.java`.
- Test sammenligner generert output mot golden-fil. `--update-goldens` for regenerering.
- Låser fast generert kode mot regresjoner.

### Runtime-tester (i `path-parser`-modul)

Eksisterende test-suite (`PathParserTest`, `CollectionPathTest`, etc.) migreres ufiltrert.
- Test-handler-klassene under `src/test/java` kjøres gjennom APT under `mvn test`.
- `new PathParser(handler)` erstattes med `PathParser.of(handler)`. Resten uendret.
- Test-feil = ekte regresjon.

### Ekvivalens-test mot 2.x (pre-release-gate)

Innsjekket Maven-modul `path-parser-3x-equivalence` aktivert via profil (`-Pequivalence`), ikke en del av standard `mvn verify`. Har både `path-parser:2.0.0` og `path-parser:3.0.0-SNAPSHOT` som dependencies, lastet via separate `URLClassLoader` for å unngå klasse-konflikt. Kjører samme XML-fixturer mot begge versjoner og sammenligner resulterende objekt-trær med AssertJ deep-compare. Kjøres manuelt før hver release; må passere før tag.

### Benchmarks (kjøres ikke i CI)

- `PathParserVsJaxbBenchmark` beholdes.
- Ny `PathParser3xVs2xBenchmark` — verifiserer 3.0 ≥ 2.x på warm JVM. Mål: 1.2-1.5× raskere.
- Ny `StartupBenchmark` med JMH `Mode.SingleShotTime` — sammenligner første `PathParser.of(handler).parse(xml)`. Forventet stor gevinst (`HandlerSpec.compile()` kjøres ikke).

### Native-image-test (CI-gate)

`.github/workflows/native-image.yml`:
- `setup-graalvm-action` med GraalVM 21.
- `native-image --no-fallback -jar test-shadow.jar` bygger uber-JAR med JUnit-runner og kjører hele suite.
- Bygg- eller test-feil → CI blokkerer release.
- 5-10 min job, kjøres på `push` til master og release-grener (ikke per PR; opt-in via `pull_request: [labeled]` med `native-image-required`).

### Ikke testet

- Endring i `@Path`-streng uten rekompilering (samme begrensning som alle APT-libs).
- Klasselastere som blander 2.x og 3.0 (out of scope; 3.0 er brudd-versjon).
- Bytecode-stabilitet på tvers av Java-versjoner (bygger på 21, kjører på 21+).

## Migrasjon for brukere

3.0 er en brudd-versjon. Migrasjon:

1. Oppgrader `path-parser`-dependency til 3.0.0.
2. Legg til `path-parser-processor` med `<scope>provided</scope>`.
3. Erstatt `new PathParser(handler)` → `PathParser.of(handler)`. `new PathParser(handler, factory)` → `PathParser.of(handler, factory)`.
4. Rekompilér.

Endringer i bruker-klasser er ikke nødvendig (samme `@Path`-API). Bare kall-sites og pom-konfig endres.

## Risiko og åpne spørsmål

- **Inkrementelle build-er** kan i edge-cases la generert kode bli stale. Maven og Gradle håndterer dette riktig i praksis, men dårlig konfigurerte build-er kan se mystiske runtime-feil. Mitigering: dokumentér, ikke prøv å detektere.
- **APT-UX i IDE** — IntelliJ og Eclipse må eksplisitt aktivere annotation processing. Vil bli dokumentert tydelig i README og 3.0 release-notes.
- **Sub-handler-rekursjon** — designet håndterer en-nivå sub-handlers (samme som 2.x). Dyp rekursjon (sub-handler kaller sub-handler kaller ...) støttes så lenge alle typer er kjent ved compile-time og ingen sykler eksisterer.
- **Custom factory og DI-frameworks** — `Function<Class<?>, Object> subFactory` består som i 2.x. DI-brukere endrer ingenting.

## Akseptansekriterier

- Alle eksisterende 2.x-tester (etter trivial API-migrasjon) passerer.
- `PathParserVsJaxbBenchmark` viser ≥ 2.x ytelse på alle størrelser (10/100/1000 orders).
- Et minimalt sample-prosjekt under `examples/native-image-smoke/` bygges som GraalVM native-image uten `reflect-config.json`-fil, og kjører `OrderHandler`-parsing korrekt på samme fixture-XML som `PathParserTest`. CI-job bygger og verifiserer dette.
- README oppdatert med 3.0-API, native-image-eksempel, migrasjons-guide.
- CI-job for native-image grønn på master.

## Out of scope

- JSON/YAML/CSV-støtte.
- XML-serialisering (`@Path`-drevet output).
- IDE-plugin for `@Path`-autocomplete.
- Automatisk migrasjons-verktøy fra 2.x til 3.0.
- Spring/Quarkus-integrasjoner (DI-fabrikken eksisterer; tilstrekkelig).

## Verifiseringskommandoer

```sh
# Test-suite + JaCoCo
mvn verify

# Bench (lokalt)
mvn -DskipTests -Pbench package
java -jar target/benchmarks.jar

# Native-image lokalt
sdk use java 21.0.x-graal
mvn -DskipTests -Pnative-image package
./target/native-image-smoke

# Pre-release ekvivalens-sjekk (manuell)
mvn -pl :path-parser-equivalence verify
```
