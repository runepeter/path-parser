# path-parser

StAX-basert XML-parser for Java som mapper elementer, attributter og tekstinnhold
til felt og metoder via xpath-lignende uttrykk i `@Path`-annotasjonen.

## Avhengighet

```xml
<dependency>
  <groupId>org.brylex</groupId>
  <artifactId>path-parser</artifactId>
  <version>3.0.0</version>
</dependency>
<dependency>
  <groupId>org.brylex</groupId>
  <artifactId>path-parser-processor</artifactId>
  <version>3.0.0</version>
  <scope>provided</scope>
</dependency>
```

Krever Java 21. Processoren genererer en parser per `@Path`-annotert
handler-klasse på kompileringstidspunkt; runtime-modulen bruker derfor
ingen refleksjon og fungerer uten konfig på GraalVM native-image.

## Bruk

Definér en handler-klasse og merk felt eller metoder med `@Path`:

```java
public class OrderHandler {

    @Path("/order/id")
    public String id;

    @Path("/order/customer")
    public String customer;

    @Path("/order/item")
    public void onItem(StartElement item) {
        // kjøres ved hvert <item>-startelement
    }
}
```

Kjør parseren mot en `Reader`, `InputStream` eller `XMLStreamReader`:

```java
OrderHandler handler = new OrderHandler();

try (Reader reader = new StringReader(xml)) {
    PathParser.of(handler).parse(reader);
}
```

## Funksjoner

### Type-konvertering

Felt og metode-parametere konverteres automatisk fra tekstinnhold til mål-typen.
Støttede typer: `String`, alle primitive typer og wrappere (`int`/`Integer`,
`long`, `double`, `boolean`, …), `BigInteger`, `BigDecimal`, `LocalDate`,
`LocalDateTime`, `Instant`, `UUID`, samt vilkårlige `enum`-typer.

```java
@Path("/order/quantity")  int quantity;
@Path("/order/price")     BigDecimal price;
@Path("/order/created")   LocalDate created;
@Path("/order/status")    Status status;          // enum
@Path("/order/uuid")      UUID id;
```

Det samme gjelder metode-parametere:

```java
@Path("/order/quantity")
public void onQuantity(int qty) { ... }
```

### Attributt-mapping

Bruk `@`-prefiks på siste path-segment for å lese et attributt:

```java
@Path("/order/@id")              String orderId;
@Path("/order/customer/@type")   String customerType;
@Path("/order/@total")           int total;          // konverteres
```

### Filtrering på attributt

Bruk `[@attr='value']` for å matche bare elementer med en spesifikk attributtverdi:

```java
@Path("/menu/food[@id='FRUIT']") String fruit;
@Path("/menu/food[@id='BREAD']") String bread;
```

### Collections

Repeterte elementer kan samles i `List`, `Set` eller `Queue`:

```java
@Path("/items/item") List<String>  items;
@Path("/prices/p")   List<BigDecimal> prices;     // konvertering pr. element
@Path("/tags/tag")   Set<String>   tags;
```

Hvis feltet er null initialiseres samlingen automatisk
(`ArrayList`, `LinkedHashSet`, eller `ArrayDeque` etter målets type). Du kan
også pre-initialisere selv.

### Sub-parsers for nestede typer

For elementer som mapper til komplekse objekter, ta inn handler-typen som
parameter — parseren oppretter en instans per match og kaller metoden ved
slutt-elementet:

```java
public class OrderHandler {

    public final List<Item> items = new ArrayList<>();

    @Path("/order/item")
    public void onItem(Item item) {
        items.add(item);
    }
}

public class Item {

    @Path("/sku")
    public String sku;

    @Path("/price")
    public BigDecimal price;
}
```

Eller direkte som collection-felt — sub-handler-instanser opprettes og legges til:

```java
public class OrderHandler {
    @Path("/order/item") List<Item> items;
}
```

### Custom instans-fabrikker

Gi en `Function<Class<?>, Object>` for å overstyre hvordan sub-handlere
opprettes — nyttig for DI-rammeverk eller tester:

```java
PathParser.of(handler, type -> injector.getInstance(type)).parse(reader);
```

Standard er kall til `getDeclaredConstructor().newInstance()`.

## Ytelse

Internt brukes en cursor-basert StAX-parser (`XMLStreamReader`) som
fôres av kompilerings-tids genererte parse-trær — null refleksjon ved
runtime. JMH-benchmarks vs Jakarta JAXB 4.0 (samme XML, samme mappet
objektmodell, 5 warmup + 10 measurement iter, JDK 21):

| Antall orders | JAXB | path-parser | Forhold |
|---:|---:|---:|---:|
| 10   | 40.8 μs  | 29.2 μs | **1.40x raskere** |
| 100  | 323 μs   | 305 μs  | 1.06x raskere |
| 1000 | 2.83 ms  | 2.99 ms | 0.95x (JAXB litt raskere) |

Path-parser er klart raskere på små og mellomstore dokumenter, mens
JAXB tar igjen ved store, repeterte strukturer. Den primære grunnen
til å velge path-parser er kombinasjonen *enkel deklarativ mapping +
ingen refleksjon* — sistnevnte gir gratis GraalVM native-image-støtte.

Kjør selv via `org.brylex.bench.BenchmarkRunner`.

## Native-image

Biblioteket fungerer uten ekstra konfigurasjon på GraalVM native-image
fordi runtime-banen er refleksjonsfri — alle handlere får en
`PathParserFactory` generert ved kompilering. Se
`examples/native-image-smoke/` for et minimalt prosjekt som bygges av
CI med `--no-fallback`.

## Migrasjon fra 2.x

3.0 er en brudd-versjon:

1. Legg til `path-parser-processor` som `<scope>provided</scope>`-dependency.
2. Erstatt `new PathParser(handler)` med `PathParser.of(handler)` (og
   tilsvarende for to-argument-formen).
3. Rekompilér — processoren genererer en parser per handler-klasse.

Refleksjons-fallbacken i 2.x er fjernet; et manglende generert
factory-oppslag gir nå `IllegalStateException` ved `PathParser.of(handler)`.

## Bygg

```sh
mvn verify
```

Test-dekning genereres av JaCoCo og legges i `target/site/jacoco/`.

Release til Maven Central:

```sh
mvn -Prelease deploy
```

## Lisens

Apache License 2.0.
