# Projection

A projection builds and maintains a **read model**: a table shaped for one query, written from events, never from the write model
at request time. It is an adapter (`*Projection` is one of the `adapters` suffixes in the architecture test's type classifier), it is
package-private, and it is the only collaborator a read controller may hold (`http.no-write-model-in-read`).

Two shapes cover projections, and they differ only in what the read model is for:

| Shape | Component | Owns | Answers | Written from |
|---|---|---|---|---|
| Read-model store | `{Area}Projection` | its own table — one row per aggregate, several denormalized views of that row | the list and the detail endpoints of its own context | the aggregate snapshot plus the foreign values it joins |
| Contract implementation | `{Consumer}Projection` | its own table — one row per aggregate, one value | a sibling context's question | the snapshot the answer is derived from |

## How to implement — handler

- Annotate the component `@Component` and `@Transactional`; a projection writes, so it needs the publisher's transaction and joins it; `projection.component`
- Write one `@EventListener` method per record type, with the type in the signature and nothing else (`handle({Snapshot})`,
  `handle({Message})`, `handle({ForeignValue})`) — Spring dispatches by parameter type, so an overload set replaces a
  `instanceof` ladder; `projection.one-handler-per-event-type`
- Start every handler with `findById(id).orElseGet(() -> new {Entity}(id))` — the same find-or-create idiom as a persistence adapter,
  so an event that arrives before its predecessor still creates a row; `projection.find-or-create`
- Read the stored state and recompute **all** derived fields from it on every event, instead of patching the field this event touches; `projection.idempotent-recompute`
- Recomputation reads only the entity being written (`entity.getSnapshot()`, `entity.getForeignValue()`), never a repository of another
  context and never the write model; `projection.no-cross-context-read`
- Ignore the value `save` returns; a projection handler returns `void` because no caller is waiting for it; `projection.void-handler`
- Tolerate any arrival order of the event types: every handler is a complete recompute from stored state, so
  `{Snapshot} → {Message} → {ForeignValue}` and `{ForeignValue} → {Message} → {Snapshot}` end in the same row; `projection.order-independent`
- Keep the handler small — it maps the event into the row; a handler with business branching belongs in a policy or in the aggregate; `projection.handler-thin` (measured: `function_statements` warning > 4, error > 8)

## How to implement — read model

- Define the read model as a package-private record next to the projection, one per endpoint shape (`{ReadModel}`, `{OtherReadModel}`),
  with a static `ofNullable(...)` factory and no logic outside it; `projection.read-model-record`
- Let `ofNullable` return `null` when the row cannot be represented (a required component is missing) instead of emitting a record with
  empty fields; the endpoint then omits the value rather than lying about it; `projection.absent-read-model-is-null`
- Derive the read model from the row's own state and the event payload, and store the result as a JSONB column — the query then reads
  one column, and the derivation runs once per write instead of once per request; `projection.derived-field-stored`
- Flatten what the client should see and keep the source under a named component: `{ReadModel}` unwraps `{Snapshot}` with
  `@JsonUnwrapped` and adds `{Message}`, so the wire shape stays flat while the storage shape stays separable; `projection.response-composition`
- Store the write-model snapshot, the last message and the sibling context's value as separate JSONB columns — one projection may hold
  copies of several contexts' data, and each copy keeps its own shape; `projection.copy-of-foreign-payload`
- Copy the columns a query filters on out of the JSON and into real columns (`{tenant}`, `{group}`), because a JSONB field cannot be
  indexed by a derived query; `persistence.filter-columns-outside-json`
- Put `@DynamicUpdate` on the read entity: independent events update the same row, and an unchanged column must not be rewritten
  or it would fight the optimistic lock; `projection.dynamic-update`
- Declare `@Version` on the read entity like on any other entity; `persistence.optimistic-version`

## How to implement — query side

- Serve the read API from the projection, not from a service: the controller injects `*Projection`, so the read path has no access to
  the write model at all; `http.no-write-model-in-read`
- Mark every query method `@Transactional(readOnly = true)`; `projection.query-readonly`
- Return the read model record from a query method, never the entity and never the write-model snapshot; `projection.return-read-model`
- Filter and page at the repository interface (`findAllBy{Group}(String)`, `findAllBy{Group}(String, Pageable)`), so the boundary of
  what a client can ask for is visible in one place; `projection.query-in-repository`
- Accept a `Pageable` and return `Page` for a collection endpoint; a `List` return for a paged endpoint breaks the paging declaration
  the controller owes the client (`http.required-paging`); `projection.paged-signature`
- Keep the query method name business-neutral (`findAll{View}`, `findAll{OtherView}`) and let the media type choose the view (`http.media-type-per-view`); `projection.query-per-view`

## Example — the read-model store

Every handler is the same four steps: get or create, set the group it owns, recompute the derived views, save. Nothing in the class
branches on business state, and nothing knows which context published the record.

```java
@Component
@Transactional
@AllArgsConstructor
class ReadModelsProjection {

    private final DeviceReadsRepository repository;

    @EventListener
    public void handle(DeviceConfiguration details) {
        DeviceReadsEntity entity = repository.findById(details.deviceId())
                .orElseGet(() -> new DeviceReadsEntity(details.deviceId()));

        entity
                .setOwnership(details.ownership())
                .setDetails(details)
                .setPin(DevicePin.ofNullable(details, entity.getStatuses()))
                .setSummary(DeviceSummary.ofNullable(details, entity.getStatuses()));

        repository.save(entity);
    }

    @EventListener
    public void handle(DeviceStatuses statuses) {
        DeviceReadsEntity entity = repository.findById(statuses.deviceId())
                .orElseGet(() -> new DeviceReadsEntity(statuses.deviceId()));

        entity
                .setStatuses(statuses)
                .setPin(DevicePin.ofNullable(entity.getDetails(), statuses))
                .setSummary(DeviceSummary.ofNullable(entity.getDetails(), statuses));

        repository.save(entity);
    }

    …   // handle(BootNotification): same four steps


    @Transactional(readOnly = true)
    public Page<DeviceSummary> findAllSummary(String provider, Pageable pageable) {
        return repository.findAllByProvider(provider, pageable)
                .map(DeviceReadsEntity::getSummary);
    }

    …   // findById and findAllPins(provider, pageable): the same read-only shape; then the repository interface

    @Data
    @Accessors(chain = true)
    @Entity
    @DynamicUpdate
    @Table(name = "device_reads")
    @NoArgsConstructor
    static class DeviceReadsEntity {

        @Id
        private String deviceId;
        @Version
        private Long version;
        private String operator;
        private String provider;

        @JdbcTypeCode(SqlTypes.JSON)
        private DevicePin pin;
        @JdbcTypeCode(SqlTypes.JSON)
        private DeviceSummary summary;
        @JdbcTypeCode(SqlTypes.JSON)
        private DeviceConfiguration details;
        @JdbcTypeCode(SqlTypes.JSON)
        private DeviceStatuses statuses;
        @JdbcTypeCode(SqlTypes.JSON)
        private BootNotification boot;
    }
}
```

## Example — a derived read model

The map view needs a coordinate and a normalised status list; both are computed once, on write.

```java
record DevicePin(
        String deviceId,
        Location.Coordinates coordinates,
        List<Status> statuses) {

    enum Status {AVAILABLE, CHARGING, FAULTED}

    static DevicePin ofNullable(DeviceConfiguration details, DeviceStatuses statuses) {
        if (details == null || details.location() == null) {
            return null;
        }
        return new DevicePin(
                details.deviceId(),
                details.location().coordinates(),
                Optional.ofNullable(statuses)
                        .map(s -> s.map(DevicePin::normalised))
                        .orElse(List.of(Status.FAULTED))
        );
    }

    …   // normalised(raw): the raw status string to Status mapping, default CHARGING
}
```

`{ReadModel}` derives the same way from the same two inputs, which is what makes the "no required component → no read model" rule
one rule instead of one per view (`projection.absent-read-model-is-null`).

## Example — a projection that implements a contract port

The consumer answers its own question from its own read table; no mediator exists because the data is projected in place
(`mediator.only-across-contexts`).

```java
@Primary
@Component
@Transactional
@AllArgsConstructor
class KnownDevicesProjection implements KnownDevices {

    private final KnownDeviceRepository repository;

    @Override
    public State get(String deviceId) {
        return repository.findById(deviceId)
                .map(KnownDeviceEntity::state)
                .orElse(State.UNKNOWN);
    }

    @EventListener
    public void handleDeInstallation(DeviceConfiguration event) {
        if (event.ownership().isUnowned()) {
            repository.save(new KnownDeviceEntity(event.deviceId(), State.UNKNOWN));
        }
    }
}
```

- Let the projection implement the consumer's contract port and carry `@Primary`, so a second implementation can be swapped in; `projection.implements-contract`
- Answer a missing row with the documented default (`{Contract}.State.UNKNOWN`), never with `Optional` or `null`, when the contract
  already has a value for "no data"; `projection.default-for-absent-row`
- Keep the projection's value vocabulary in the contract (`{Contract}.State`), not in the entity; the entity column is
  `@Enumerated(STRING)`; `projection.state-from-contract`

## Tests a projection needs

The integration test calls the handlers **directly** and asserts the resulting JSON — no event infrastructure, no controller, no sleep.

```java
@IntegrationTest
@Transactional
class ReadModelsProjectionTest {

    @Autowired
    ReadModelsProjection projection;
    @Autowired
    ReadModelsProjection.DeviceReadsRepository repo;

    final String deviceId = "fixed-device-id";

    @BeforeEach
    void setUp() {
        repo.deleteAll();
    }

    @Test
    void threeRecordsBecomeOneRow() {
        givenDevice();

        assertThat(projection.findAllPins("public-devices", Pageable.unpaged()).getContent())
                .isExactlyLike("""
                        [{"deviceId": "fixed-device-id",
                          "coordinates": {"longitude": 51.0983, "latitude": 16.9317},
                          "statuses": ["CHARGING"]}]
                        """);
    }

    @Test
    void theSameRecordTwiceKeepsOneRow() {
        givenDevice();
        DeviceStatuses statuses = CommunicationFixture.statuses(deviceId);
        projection.handle(statuses);
        projection.handle(statuses);

        assertThat(projection.findAllPins("public-devices", Pageable.unpaged()).getContent())
                .isExactlyLike("""
                        [{"deviceId": "fixed-device-id",
                          "coordinates": {"longitude": 51.0983, "latitude": 16.9317},
                          "statuses": ["CHARGING"]}]
                        """);
    }

    private void givenDevice() {
        projection.handle(DeviceFixture.givenDeviceConfiguration(deviceId));
        projection.handle(CommunicationFixture.boot(deviceId));
        projection.handle(CommunicationFixture.statuses(deviceId));
    }
}
```

- Drive the projection by calling its handlers, not by publishing events — the topic under test is the projection, not the dispatcher; `test.projection-handlers-direct`
- Assert the read model as JSON so the storage shape and the wire shape are both pinned by one assertion (`assertThat(...).isExactlyLike(...)`); `test`.`json-roundtrip`
- Clear the projection's own table in `@BeforeEach` and use a fixed id, so the expected JSON is literal and diffable; `test.fixed-id`
- Add the re-delivery case with every handler: the same event twice must produce the same row (`projection.idempotent-recompute`); `test.projection-replay-idempotent` (reviewed)
- Add the out-of-order case for every pair of handlers you accept (`projection.order-independent`), because that claim is only a claim
  until a test pins it

## What to avoid / NOGO

- Do not query the write model from a read endpoint — a `GET` controller holding a `*Service` is the failure this pattern removes; `http.no-write-model-in-read`
- Do not inject a `*Service` or another context's `*Repository` into a projection: everything it needs arrives as an event; `projection.no-cross-context-read`
- Do not patch one field per event and leave the rest stale — recompute the derived views from stored state every time; `projection.idempotent-recompute`
- Do not put business rules in a projection: it maps, it does not decide; `mediator.no-logic` for the same reason, and
  `policy.no-inline-decision` when the urge to branch appears
- Do not let the projection own the schema of the write model — it owns its own read table and nothing else; `projection.own-table`
- Do not return the entity from a query method, and do not let a controller receive a `*Entity`; `http.no-persistence-type-in-api`
- Do not make a projection `public` — it is a bean resolved by type inside its context; `structure.adapter-visibility`
- Do not add a second projection over the same table for a new endpoint: add a query method and a read-model record; `projection.query-per-view` (reviewed)

## Enforcement

Measured: `projection.handler-thin` — `function_statements` warning > 4, error > 8 on methods inside a `*Projection`, selected by the
`*Projection` type-name scope in the `sqt` config.

Not encoded yet, though the classifier input exists (`name.projection` = a type name ending in `Projection`, a member of `adapter-only`).
Cheap to encode: `projection.component`, `projection.void-handler`, `projection.query-readonly`, `projection.return-read-model`,
`projection.no-cross-context-read` (a `*Service`/`*Repository` field inside a `*Projection`).
`projection.idempotent-recompute` and `projection.order-independent` are review rules — they are claims about behaviour over time, not
about a syntax shape.
