# Persistence Adapters

A persistence adapter is a **secondary adapter**: it implements one secondary port (`{Aggregate}Repository`) with one storage
strategy and nothing else. Four storage shapes are known to work behind the same two-method port — document snapshot, document plus
history, event sourcing, normalized tables — and each has its own read-back test. That is the point of the pattern: storage is a
decision made at wiring time, not a property of the model.

| Strategy | Stores | Reads by | Read-back test |
|---|---|---|---|
| Document snapshot | the aggregate as one JSONB column | one row lookup | one round-trip test |
| Document + history | JSONB column **and** one row per emitted event | one row lookup | one round-trip test |
| Event sourcing | one row per event, no snapshot | replay of the last event per type | one round-trip test |
| Normalizing | relational columns + child rows | join, reassembled field by field | one round-trip test |

## How to implement — the adapter

- Implement exactly one secondary port, keep the class package-private, and name it `{Shape}{Aggregate}Repository` — the port keeps the
  plain `{Aggregate}Repository` name, so the shape is what tells the two apart; `persistence.implements-port`
- Let exactly **one** adapter per port carry `@Primary`, so injection by interface resolves; the other strategies stay beans reachable
  by their concrete type from tests only; `persistence.primary-strategy`
- Keep exactly one strategy per class — a class that can write both a document and an event row is two strategies glued together; `persistence.one-strategy-per-class` (reviewed)
- Nest the JPA entity and the Spring Data interface **inside** the adapter; the only top-level types are the adapter and the port; `structure.nested-entity`
- Map, never decide: the adapter sets fields and drains the event buffer, it holds no invariant and no calculation; `persistence.mapping-only`
- Keep the two port methods small — `get` and `save` carry the storage decision, so they are bounded at `function_statements` warning > 8, error > 16; `persistence.port-method-size`
- Leave the private mapping helpers (`setAggregate`, `of`, `childRows`) **unbounded**: a helper is as long as the snapshot is wide, not
  because it is complex, and a size bound there would only encourage hiding columns. What must not appear in a helper is a rule — that
  is `persistence.mapping-only`, a review rule, not a metric; `persistence.mapping-helper-unbounded` (reviewed)
- Write through `findById(...).orElseGet(() -> new {Entity}(id))` then `set…`/`save` — one code path for insert and update, so a strategy
  cannot diverge between the two cases; `persistence.find-or-create`
- Rehydrate the aggregate by calling its all-args constructor — the constructor exists for this adapter and for the factory only; `aggregate.all-args-constructor`
- Convert the aggregate to storage through its snapshot query (`{Editor}.toSnapshot()`), never by reading its fields directly; `persistence.snapshot-to-storage`
- Declare `@Version` on the entity and a `version bigint` column, letting the optimistic lock fail a lost update instead of silently
  overwriting it; `persistence.optimistic-version`
- Store a whole snapshot in a JSONB column with `@JdbcTypeCode(SqlTypes.JSON)` on the field — the type stays a plain record with no
  JPA mapping; `persistence.json-column`
- Type the column `JsonNode` and convert in the adapter when the stored value embeds a sealed hierarchy that carries no
  `@JsonTypeInfo`: Jackson writes such a value and cannot read it back, so the record cannot be the column type — the conversion lives
  in the adapter, with a `private static final` derived mapper and a mix-in that adds the discriminator; `persistence.json-column`
- Keep the columns a query filters on outside the JSON, and leave everything only ever read as a whole inside it; `persistence.filter-columns-outside-json`
- Name the table after the strategy's data, not after the class (`{table}`, `{events_table}`, `{table}` + `{child_table}`); `persistence.table-name`
- Stop after the write: publishing events and snapshots is the adapter's last step, and only the adapters that own history do it; `persistence.publish-after-write`

## How to implement — the schema

- Declare every table in the Liquibase master changelog, one changeSet per table, id `<NNNN>-<table>`; `persistence.liquibase-changeset`
- Keep `spring.jpa.hibernate.ddl-auto: none` in every profile — Hibernate never owns the schema, the configured
  `liquibase.change-log` master changelog does; `persistence.no-ddl-auto`
- Never edit a changeSet that has run anywhere: ids are monotonic and never reused; `persistence.changeset-immutable` (reviewed)
- Give the `version` column `defaultValueNumeric: 1` and `nullable: false`, matching `@Version long`/`Long` on the entity; `persistence.optimistic-version`
- Use `jsonb` for a column mapped to a record and `numeric(18, 15)` for a decimal that must survive the round trip; `persistence.column-type-matches-java`
- Let the integration profile recreate the schema (`drop-first: true`) and the production profile never do it; `persistence.no-ddl-auto`
- Wire the JSON format mapper once, on the boot configuration — never per entity; `json.hibernate-format-mapper`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate.type.json_format_mapper: <root-package>.tools.JacksonJsonFormatMapper
  liquibase:
    change-log: classpath:db/changelog.yaml
```

## How to implement — the event-buffer contract

The aggregate appends and the adapter drains. Every history-owning strategy does it in the same four lines — copied, cleared,
returned — under a name of its own. The shape is part of the contract, not an implementation detail:

```java
class DocumentOrderRepository implements OrderRepository {

    static List<DomainEvent> eventsEmittedFrom(OrderEditor order) {
        List<DomainEvent> emitted = List.copyOf(order.events);
        order.events.clear();
        return emitted;
    }

    …   // get, save, the nested entities and the two Spring Data interfaces
}
```

- Copy the buffer and clear it **before** writing, so a failed save does not leave events that the next save would publish twice; `persistence.drain-events`
- Publish the snapshot once per changed save, and the domain events once each, after the rows are written; `persistence.publish-after-write`
- Never publish for a save that emitted nothing — that is what makes the no-op `PATCH` free; `integration.publish-only-on-change`
- Treat the publication order between the snapshot and the domain events as unspecified: one strategy may publish the snapshot first and
  another the events first, so a listener must never depend on seeing one before the other; `integration.publication-order-unspecified`

## Example — document with history

One JSONB row for the current state, one row per event, the snapshot published only when something changed.

```java
@Primary
@Repository
class DocumentOrderRepository implements OrderRepository {

    private final OrderDocumentRepository documents;
    private final OrderEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    DocumentOrderRepository(OrderDocumentRepository documents, OrderEventRepository events,
                            ApplicationEventPublisher publisher, Clock clock) {
        this.documents = documents;
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Optional<OrderEditor> get(String orderId) {
        return documents.findById(orderId).map(OrderDocumentEntity::getOrder);
    }

    @Override
    public void save(OrderEditor order) {
        List<DomainEvent> emitted = eventsEmittedFrom(order);
        Instant now = Instant.now(clock);
        documents.save(documents.findById(order.orderId)
                .orElseGet(() -> new OrderDocumentEntity(order.orderId))
                .setOrder(order));
        emitted.forEach(event -> events.save(new OrderEventEntity(order.orderId, now, event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(order.toSnapshot());
        }
        emitted.forEach(publisher::publishEvent);
    }

    interface OrderDocumentRepository extends JpaRepository<OrderDocumentEntity, String> {
    }

    interface OrderEventRepository extends JpaRepository<OrderEventEntity, Long> {

        List<OrderEventEntity> findByOrderId(String orderId);
    }

    @Entity
    @Table(name = "order_document")
    static class OrderDocumentEntity {

        @Id
        private String orderId;
        @Version
        private long version;
        @JdbcTypeCode(SqlTypes.JSON)
        private OrderEditor order;

        protected OrderDocumentEntity() {
        }

        OrderDocumentEntity(String orderId) {
            this.orderId = orderId;
        }

        OrderEditor getOrder() {
            return order;
        }

        OrderDocumentEntity setOrder(OrderEditor order) {
            this.order = order;
            return this;
        }
    }

    @Entity
    @Table(name = "order_events")
    static class OrderEventEntity {

        @Id
        @GeneratedValue
        private Long id;
        private String orderId;
        private Instant time;
        @JdbcTypeCode(SqlTypes.JSON)
        private DomainEvent event;

        protected OrderEventEntity() {
        }

        OrderEventEntity(String orderId, Instant time, DomainEvent event) {
            this.orderId = orderId;
            this.time = time;
            this.event = event;
        }
    }
}
```

The entity is nested, package-private and carries the storage annotations; the aggregate carries none (`model.no-persistence-types`).
The timestamp comes from the injected clock, never from `Instant.now()` inside the entity (`clock.no-static-now`).

## Example — event sourcing (replay instead of snapshot)

No snapshot table: `get` replays, `save` appends.

```java
@Repository
class EventSourcedOrderRepository implements OrderRepository {

    private static final ObjectMapper EVENTS = JsonMapperConfiguration.OBJECT_MAPPER.rebuild()
            .registerSubtypes(new NamedType(OrderPlacedV1.class, "OrderPlaced_v1"))
            .build();

    private final OrderEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    …   // constructor

    @Override
    public Optional<OrderEditor> get(String orderId) {
        List<DomainEvent> history = events.findByOrderId(orderId).stream()
                .map(entity -> read(entity.event))
                .map(EventSourcedOrderRepository::upcast)
                .collect(Collectors.toList());
        if (history.isEmpty()) {
            return Optional.empty();
        }
        Collections.reverse(history);
        LastEventPerType last = LastEventPerType.of(history);
        return Optional.of(new OrderEditor(orderId, new ArrayList<>(),
                last.getOrNull(OrderPlaced.class, OrderPlaced::placedAt),
                last.getOrDefault(StatusChanged.class, StatusChanged::status, Status.pending()),
                last.getOrDefault(NoteUpdated.class, NoteUpdated::note, Note.empty())
        ));
    }

    @Override
    public void save(OrderEditor order) {
        List<DomainEvent> emitted = eventsEmittedFrom(order);
        Instant now = Instant.now(clock);
        emitted.forEach(event -> events.save(new OrderEventEntity(order.orderId, now, event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(order.toSnapshot());
        }
        emitted.forEach(publisher::publishEvent);
    }

    private static DomainEvent read(JsonNode json) {
        try {
            return EVENTS.treeToValue(json, DomainEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("unreadable event row", e);
        }
    }

    private static DomainEvent upcast(DomainEvent event) {
        return switch (event) {
            case OrderPlacedV1 legacy -> new OrderPlaced(legacy.placedAt());
            …   // one case per legacy shape
        };
    }

    …   // lastEventPerType, eventsEmittedFrom, OrderEventRepository, OrderEventEntity

    record LastEventPerType(Map<Class<?>, DomainEvent> byType) {

        static LastEventPerType of(List<DomainEvent> history) {
            Map<Class<?>, DomainEvent> byType = new HashMap<>();
            history.forEach(event -> byType.put(event.getClass(), event));
            return new LastEventPerType(byType);
        }

        <E extends DomainEvent, V> V getOrNull(Class<E> type, Function<E, V> reader) {
            E event = type.cast(byType.get(type));
            return event == null ? null : reader.apply(event);
        }

        <E extends DomainEvent, V> V getOrDefault(Class<E> type, Function<E, V> reader, V fallback) {
            V value = getOrNull(type, reader);
            return value == null ? fallback : value;
        }
    }

    …   // the sealed DomainEvent subtypes: OrderPlaced, StatusChanged, NoteUpdated, OrderPlacedV1
}
```

- Push the "last event per type" reduction into one native query (`select distinct on (type) … order by type, time desc`) rather
  than loading the whole history into the JVM; `persistence.replay-in-query`
- Rebuild the aggregate with the all-args constructor and the defaults from the domain, never with nulls; `persistence.rehydrate-defaults`
- Upcast a legacy event shape on read with an exhaustive `switch` over the sealed event union, so a persisted v1 becomes the current
  record before it reaches the aggregate; `event.upcast-on-read`
- Keep the derived mapper `private static final` in the adapter that needs the legacy subtype — the process-wide mapper is not modified; `json.derived-mapper-declared`
- Start the replayed buffer empty (`new ArrayList<>()`): a rehydration must not re-emit history; `event.replay-no-emit`

## Example — normalizing

The strategy that proves the port is strategy-agnostic: nothing JSON, one row per aggregate, one row per child, and an explicit
field-by-field mapping in both directions.

```java
@Repository
class NormalizingOrderRepository implements OrderRepository {

    …   // get, save, the Spring Data interface, and the OrderSnapshot the editor exposes

    @Entity
    @Table(name = "order")
    static class OrderEntity {

        @Id
        private String orderId;
        @Version
        private long version;
        private String customerId;
        private String channel;
        private String deliveryStreet;
        …   // the remaining delivery columns and the three status flags

        @OneToMany(orphanRemoval = true, cascade = CascadeType.ALL)
        @JoinColumn(name = "order_id", referencedColumnName = "order_id")
        private List<OrderLineEntity> lines;

        void setOrder(OrderEditor editor) {
            OrderSnapshot order = editor.toSnapshot();
            this.customerId = order.customer().id();
            this.channel = order.channel();
            this.deliveryStreet = order.delivery() == null ? null : order.delivery().street();
            …   // the remaining delivery columns, the three status flags, lines(order.lines())
        }
    }

    @Entity
    @Table(name = "order_lines")
    static class OrderLineEntity {

        @Id
        @GeneratedValue
        private Long id;
        private String sku;
        private int quantity;
    }
}
```

- Keep the relational mapping exhaustive and explicit — every column assigned from the snapshot, every value object rebuilt from
  columns; a shared `@Embeddable` would tie two strategies' schemas together; `persistence.explicit-mapping` (reviewed)
- Encode an "absent" value object as `null` columns and read it back as `null`, not as an empty object; `persistence.null-object-absent`
- Give a child row an id that the schema or the mapping assigns: a child entity whose `@Id` has no `@GeneratedValue` and which the
  mapping never fills in makes every parent with children unwritable, and a test that only saves a parent with no children will not
  see it; `persistence.child-id-assigned` (held at `warning`)

## Example — a configuration document as a port

Not an aggregate: the port returns the value object, the adapter stores it through the generic JSONB configuration store.

```java
@Repository
class PricingRulesDocumentRepository implements PricingRulesRepository {

    public static final String CONFIG_NAME = "PricingRules";

    …   // ConfigurationStore: the generic store from the infrastructure patterns
    …   // PricingRules and PricingRulesRepository: the defaulted value object and its port

    private final ConfigurationStore store;

    PricingRulesDocumentRepository(ConfigurationStore store) {
        this.store = store;
    }

    @Override
    public PricingRules get() {
        return store.get(CONFIG_NAME)
                .as(PricingRules.class)
                .orElse(PricingRules.defaultRules());
    }
}
```

- Store an editable document under a stable `CONFIG_NAME` and fall back to the domain default when the row is absent; `persistence.config-document-key`
- Annotate the adapter `@Repository` like every other adapter — `@Service` hides a persistence adapter from the `adapters` classifier;
  `persistence.adapter-annotation` (held at `warning`)
- Keep the document free of the aggregate: a configuration is a value, so the port returns it directly; `port.secondary-value-signature`

## Tests — one shape, one file per strategy

Every strategy has its own integration test in the same two steps — save through one transaction, read through another, assert
against a fixture — with an assertion list that differs per strategy.

```java
@IntegrationTest
@Transactional
class NormalizingOrderRepositoryTest {

    @Autowired
    NormalizingOrderRepository repository;

    @Test
    void saveAndGetOrder() {
        OrderEditor saved = OrderFixture.givenOrder();
        transactional(() -> repository.save(saved));
        Optional<OrderEditor> read = transactional(() -> repository.get(saved.orderId));

        assertThat(read).isExactlyLike(saved);
        assertThat(read).hasFieldsLike("""
                { "customer": { "id": "C-1" }, "channel": "web", … }
                """);
    }
}
```

- Autowire the **concrete adapter**, not the port — the port resolves to the `@Primary` bean, so a port-typed test would only ever
  test one strategy; `persistence.test-concrete-adapter`
- Commit the write in its own transaction (`TestTransaction.transactional(...)`) and read in another, so the test exercises the
  database and not Hibernate's first-level cache; `test.integration-committing-read`
- Assert the round trip with `JsonAssert.isExactlyLike` on the whole aggregate (`test.json-roundtrip`) and the strategy-specific shape
  with `hasFieldsLike` (`test.json-partial`) — a JSON snapshot of the row is also the cheapest review artefact for a mapping change; `persistence.test-json-snapshot`
- Assert the published events in the same test through `@RecordApplicationEvents`, because publishing is part of the adapter's
  contract; `test.record-events`

## What to avoid / NOGO

- Do not add a persistence annotation or a Spring Data type to the aggregate, the snapshot or a value object; `model.no-persistence-types`
- Do not let the adapter call a service, an aggregate command or a calculation — a strategy maps storage, it does not run a use case; `persistence.mapping-only`
- Do not expose the entity: it stays a nested `static class` and never appears in a port signature, a controller or a projection; `structure.nested-entity`
- Do not enable `ddl-auto: update`, and do not let a test rely on it — a schema that only exists in a test profile hides the missing
  changeSet; `persistence.no-ddl-auto`
- Do not put two strategies in one bean (`if (storeHistory)`) — that is two adapters with one name; `persistence.one-strategy-per-class`
- Do not edit or renumber a changeSet after it has run anywhere; add a new one; `persistence.changeset-immutable` (reviewed)
- Do not annotate a persistence adapter `@Service` — the annotation hides it from the `adapters` classifier; `persistence.adapter-annotation`
- Do not write a `@Transactional` on the adapter: the transaction belongs to the service that owns the use case; `persistence.no-transaction` (reviewed)

## Enforcement

Encoded in the `scope:` bands of `.agents/sqt.yaml` (`persistence.port-method-size`); the table also records the deliberate
absence:

| Id | Shape of the check |
|---|---|
| `persistence.port-method-size` | `function_statements` warning > 8, error > 16 on `get`/`save` only, scoped to the two port methods of an adapter |
| `persistence.mapping-helper-unbounded` | the deliberate absence of a band on the private mappers: their length is the width of the snapshot, and what must not appear in one is a rule |

`persistence.no-ddl-auto` and `persistence.liquibase-changeset` are file-level facts on the boot configuration and the master
changelog and the cheapest to encode; `persistence.optimistic-version` and the adapter shape (`@Repository`, `@Primary`, one
`*Repository` class per strategy) fit the existing selector set (`name.repository`, `in.repository`, `framework-anywhere`).
`persistence.test-concrete-adapter` and the `@Primary` uniqueness are review rules — a count over beans is not expressible in the
current rule families.
