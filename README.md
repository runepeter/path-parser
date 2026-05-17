# path-parser

StAX-basert XML-parser for Java som mapper elementer og tekstinnhold til felt og
metoder via xpath-lignende uttrykk i `@Path`-annotasjonen.

## Avhengighet

```xml
<dependency>
  <groupId>org.brylex</groupId>
  <artifactId>path-parser</artifactId>
  <version>1-SNAPSHOT</version>
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
    public String price;
}
```

### Attributt-matching

Bruk `[@attr='value']`-syntaks for å filtrere på attributt:

```java
@Path("/menu/food[@id='FRUIT']")
public String fruit;
```

## Bygg

```sh
mvn verify
```

## Lisens

Apache License 2.0.
