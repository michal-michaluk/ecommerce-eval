# Integration Events

Contexts talk through Spring's in-process `ApplicationEventPublisher`. A **publisher** is always an adapter — a persistence adapter
that just wrote, or a protocol adapter that just received a message — and a **listener** is always a component in the consuming
context. The aggregate appends domain events; everything that leaves the process boundary of the aggregate is this mechanism.

Two record kinds cover every publication: the domain events the aggregate produced, and the snapshot of the aggregate after the
write. A protocol adapter adds the message it received; a sibling context adds the value it owns. Each kind has exactly one
publisher role and one consumer role:

| Published record | Published by | Consumed by |
|---|---|---|
| each `DomainEvent` the aggregate appended | the adapter that drained the event buffer, one publication per event | any consumer that cares about the fact; no consumer is required |
| the aggregate snapshot `{Snapshot}` | the same adapter, once per changed write | every `*Projection` that stores that shape |
| a message `{Message}` received from a protocol peer | the protocol adapter that received it, after its service answered | the projection that derives a view from it |
| a foreign value `{ForeignValue}` owned by a sibling context | the context that owns it | the projection that joins it into its own row |

The transport is the Spring `ApplicationEventMulticaster`: no Kafka topic, no outbox worker, no `@Async`. Adding a broker is a
deliberate `integration.single-transport` decision, not a default.

## How to implement — publishing

- Inject `ApplicationEventPublisher` into the adapter, never into the aggregate and never into the primary port; `integration.publish-in-adapter`
- Publish **after** the write and after the event buffer has been drained, so a listener that reads the database sees the new state; `event.published-after-save`
- Publish the aggregate snapshot as a separate fact once per changed save (`if (!emitted.isEmpty())`) and each domain event once, so a
  no-op command produces no traffic; `event.snapshot-published`, `integration.publish-only-on-change`
- Treat the pair as two independent signals, not as an ordered pair: one adapter may publish the snapshot first and another last, so no
  consumer may depend on the sequence; `integration.publication-order-unspecified`
- Publish a **public record** whose components carry everything a consumer needs — the snapshot is a `public record {Snapshot}`, a
  protocol fact is a `public record {Message}`; a listener must never call back into the publisher for more data; `integration.payload-complete`
- Name the published type for what happened, in the past tense, and reuse the domain type rather than inventing a `*Event` envelope; `event.past-tense`
- Publish from every adapter that owns the same write path — a write path with two interchangeable adapters, one publishing and one
  silent, stops all read-model updates the moment the silent one becomes primary; `integration.every-adapter-publishes` (encode at `warning` first, promote to `error` when the adapters agree)
- Never publish from a primary port: the publisher is adapter infrastructure. An architecture test that whitelists service dependencies
  rejects `org.springframework.context` in a `*Service` while still allowing `org.springframework.context.event` for `@EventListener`; `integration.publish-in-adapter`
- Keep publication inside the transaction (no `@TransactionalEventListener`, no `@Async`) so the write and the projection commit or roll
  back together; `integration.synchronous-in-transaction`

## How to implement — listening

- Put the listener in the **consuming** context, on the projection or bean that owns the read model it updates; `integration.listener-in-consumer`
- Overload `handle` per record type and let Spring dispatch on the parameter type — no `instanceof`, no common interface, no generic
  `handle(Object)`; `integration.listener-by-payload-type`
- Mark the listener method `public` (Spring needs to proxy/invoke it) but keep the class package-private; `structure.override-visibility`
- Join the publisher's transaction (`@Transactional` on the component), so a listener exception rolls the write back instead of leaving a
  row that disagrees with its source; `integration.listener-joins-transaction`
- Make the listener idempotent — it runs again on every re-publication and on every retry of the outer transaction; `projection.idempotent-recompute`
- Do only local work: read your own table, write your own table. A listener that calls another context's service is a mediator in
  disguise; `integration.listener-local-only`
- Keep no state in the listener and schedule nothing from it; `@Scheduled` on a listener turns an event into a timer; `integration.listener-stateless` (reviewed)
- Answer a question that must return a value with a contract port, not with an event — an event carries no reply; `port.contract-consumer-question`

## Example — publish from the adapter

The adapter owns the whole sequence: drain the buffer, write, then publish. The aggregate never sees the publisher.

```java
@Repository
@AllArgsConstructor
class DeviceDocumentWithHistoryRepository implements DeviceRepository {

    private final DocumentRepository documents;
    private final EventRepository events;
    private final ApplicationEventPublisher publisher;

    @Override
    public void save(DeviceConfigurationEditor device) {
        List<DomainEvent> emitted = device.events();
        documents.save(documents.findById(device.deviceId())
                .orElseGet(() -> new DeviceDocumentEntity(device.deviceId()))
                .setDevice(device));
        emitted.forEach(event -> events.save(
                new DeviceEventEntity(device.deviceId(), event)
        ));
        device.clearEvents();
        if (!emitted.isEmpty()) {
            publisher.publishEvent(device.toDeviceConfiguration());
        }
        emitted.forEach(publisher::publishEvent);
    }
}
```

The aggregate only appended to its `events` field (`aggregate.events-field`); the adapter copied and cleared the buffer
(`persistence.drain-events`), wrote the rows, and only then published.

## Example — listen in the consumer

One overload set per projection: one method per record type, each one a find-or-create plus a local write. What the handler maps
into its row is the projection's own business (see the projection playbook); the shape that matters here is the dispatch.

```java
@Component
@Transactional
@AllArgsConstructor
class ReadModelsProjection {

    private final DeviceReadsRepository repository;

    @EventListener
    public void handle(DeviceConfiguration snapshot) {
        DeviceReadsEntity entity = getOrCreate(snapshot.deviceId());
        entity.setDetails(snapshot);
        repository.save(entity);
    }

    @EventListener
    public void handle(BootNotification message) {
        DeviceReadsEntity entity = getOrCreate(message.deviceId());
        entity.setBoot(message);
        repository.save(entity);
    }

    private DeviceReadsEntity getOrCreate(String deviceId) {
        return repository.findById(deviceId)
                .orElseGet(() -> new DeviceReadsEntity(deviceId));
    }
}
```

A second consumer answers a question a sibling context asks, from its own table and the same snapshot:

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
    public void handle(DeviceConfiguration snapshot) {
        repository.save(new KnownDeviceEntity(snapshot.deviceId(), State.KNOWN));
    }
}
```

Two contexts, one published record, no import of the publisher's context other than the record itself — which is the point: the
snapshot record is in the producer's exposed list (`boundary.exposed-type-list`).

## Example — a message adapter publishing what it received

The message is both the request the protocol peer sends and the fact a read model consumes; no translation layer and no second
record. The adapter publishes, the service does not.

```java
@Component
@AllArgsConstructor
class BootNotificationAdapter {

    private final CommunicationService service;
    private final ApplicationEventPublisher publisher;

    void onMessage(BootNotification boot) {
        service.handleBoot(boot);
        publisher.publishEvent(boot);
    }
}
```

The service computes the answer and holds no publisher; the adapter publishes the fact. Because dispatch is synchronous and
in-transaction, every listener has already run by the time `onMessage` returns — the caller does not *depend* on them, but it is not
answered before them either.

## Testing

```java
@IntegrationTest
@Transactional
@RecordApplicationEvents
class DeviceDocumentWithHistoryRepositoryTest {

    @Autowired
    private DeviceRepository repository;
    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    private ApplicationEvents emitted;

    @Test
    void emitsDomainEvents() {
        DeviceConfigurationEditor saved = DeviceFixture.givenStepByStepConfiguredDevice();
        transactional(() -> repository.save(saved));

        assertThat(emitted.stream(DomainEvent.class))
                .containsExactly(
                        new OwnershipUpdated(saved.deviceId(), DeviceFixture.ownership()),
                        new LocationUpdated(saved.deviceId(), DeviceFixture.location())
                );
        assertThat(emitted.stream(DeviceConfiguration.class))
                .containsExactly(saved.toDeviceConfiguration());
    }
}
```

- Assert publication with `@RecordApplicationEvents` + `ApplicationEvents.stream(Type.class)`; the recorder captures in-process events
  and needs no listener of its own; `test.record-events`
- Assert exactly the records the use case should produce (`containsExactly`) — an extra event is a defect, not a detail; `test.events-exact`
- Assert the listener side by calling the projection handler directly in its own test (`test.projection-handlers-direct`), so a broken
  publisher and a broken projection fail in different tests; `test.separate-publisher-and-listener`
- Commit through `TestTransaction.transactional(...)` before asserting, because the publication happens inside the adapter's
  transaction; `test.integration-committing-read`

## What to avoid / NOGO

- Do not publish from an aggregate, a value object or a policy — `aggregate.no-io` and `policy.no-io` already forbid the dependency; `integration.publish-in-adapter`
- Do not publish before the write, and do not publish and then keep the buffer: the next save would publish the same event twice; `persistence.drain-events`
- Do not rely on the snapshot and the domain events arriving in a fixed order — two adapters over one write model need not agree; `integration.publication-order-unspecified`
- Do not use `@TransactionalEventListener(phase = AFTER_COMMIT)` for a read-model update you expect to be consistent with the write: the
  listener then runs outside the transaction that can still fail; `integration.synchronous-in-transaction`
- Do not add `@Async` or an executor: a read model updated later is a read model that is wrong now; `integration.no-async-listener` (reviewed)
- Do not build an event envelope (`IntegrationEvent<T>`, `{type, payload, id}`) for an in-process event — the record type *is* the
  envelope; `integration.no-envelope`
- Do not use an event where a value is needed back; that is a contract port (`port.contract-consumer-question`);
  `mediator.no-logic`
- Do not add a message broker alongside the in-process publisher without deciding which one is authoritative — two transports will
  diverge on the first failure; `integration.single-transport` (reviewed)

## Enforcement

Not encoded yet. Expressible with the existing classifiers: `name.listener` (`(Listener|Client|Integration)$`), `in.tests`,
`name.projection`. Cheap shape checks: `integration.publish-in-adapter` (an `ApplicationEventPublisher` field is allowed only in
`adapter-only` types — the inverse of `aggregate.no-io`/`policy.no-io`), `integration.listener-by-payload-type`,
`integration.listener-local-only`. `integration.synchronous-in-transaction` and `integration.single-transport` are dependency facts
(`@TransactionalEventListener`, `@Async`, a broker package) and belong in the dependency graph, not in a shape check.
