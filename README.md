# path-parser

StAX-basert XML-parser for Java som mapper elementer, attributter og tekstinnhold
til felt og metoder via xpath-lignende uttrykk i `@Path`-annotasjonen.

## Avhengighet

```xml
<dependency>
  <groupId>org.brylex</groupId>
  <artifactId>path-parser</artifactId>
  <version>2.0.0</version>
</dependency>
```

Krever Java 21.

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

Kjør parseren mot en `XMLEventReader`:

```java
OrderHandler handler = new OrderHandler();

try (Reader reader = new StringReader(xml)) {
    XMLEventReader events = XMLInputFactory.newInstance().createXMLEventReader(reader);
    new PathParser(handler).parse(events);
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
new PathParser(handler, type -> injector.getInstance(type)).parse(events);
```

Standard er kall til `getDeclaredConstructor().newInstance()`.

## Ytelse

Internt brukes en cursor-basert StAX-parser (`XMLStreamReader`) med pre-
kompilerte path-trær per handler-klasse. JMH-benchmarks vs Jakarta JAXB 4.0
(samme XML, samme mappet objektmodell):

| Antall orders | JAXB | path-parser | Forhold |
|---:|---:|---:|---:|
| 10   | 190 μs   | 96 μs   | **1.98x raskere** |
| 100  | 1.40 ms  | 0.87 ms | **1.60x raskere** |
| 1000 | 12.8 ms  | 8.8 ms  | **1.46x raskere** |

Allokering per parse er ~9 MB vs JAXBs ~4 MB ved 1000 orders. Kjør selv via
`org.brylex.bench.BenchmarkRunner`.

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
