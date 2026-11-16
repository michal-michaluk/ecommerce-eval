# Domain Event

A domain event records a fact that has already happened inside one aggregate. Events are the only way state changes leave a
bounded context: they feed read models, other contexts and external integrations, and they are the persisted history when
event sourcing is used.

The aggregate appends events. The repository drains and publishes them. Nothing else touches them.

Events are model code, so the model-isolation rules apply to them: no framework or persistence type, no repository/service field, no setter
(`model.forbidden-imports`, `model.no-persistence-types`, `model.no-setters`).

## How to implement — common to all contexts

- Declare one `DomainEvent` type per context and put each event as a `record` nested inside it; `event.nested-record`
- Name the event in the past tense after the fact (`{Entity}Updated`, `{Entity}Assigned`, `{Process}Completed`); `event.past-tense`
- Version every event in its `@JsonSubTypes` name as `<RecordName>_v<N>` — the `_v` part is required and validated at startup for every
  context whose `DomainEvent` union is registered in `tools/` (the JSON configuration). The persisted event `type` column stores only the
  version-less part of that name; a small `Type(name, version)` value splits it, and the version is what a future upcast will read; `event.name-version`
- Keep the subtype name prefix equal to the record's simple name; the name is the persisted `type`, so a drifting name leaves a history
  whose rows no longer name the fact — deserialisation still works, the stored history stops being readable by a human; `event.name-matches-record`
- Type the payload as identity plus the changed value object — `event.payload-identity-plus-change` (reviewed: it is a shape rule,
  only the persistence-type part is automated); never the aggregate, never a JPA entity, never a transport DTO; `model.no-persistence-types`
- Append the event from the aggregate inside the change-detection branch, never from a service or adapter; `aggregate.event-with-change`
- Let the repository drain with `List.copyOf(...)` followed by `clear()` on the aggregate's buffer; the aggregate exposes no drain method; `event.drained-by-repository`
- Issue the event write before publishing it, and publish each event once; a service method is `@Transactional`, so publication happens
  inside the transaction and before commit — the guarantee is ordering of *statements*, not "after commit", and a listener must therefore
  tolerate a transaction that may still roll back; `event.published-after-save`
- Publish the aggregate snapshot as a separate fact (a `{Snapshot}` record) when a projection consumes it; `event.snapshot-published`
- Emit at least one event per change and no event for a no-op command; `aggregate.event-with-change`
- Keep events immutable records — `event.immutable` (reviewed; the automated part is `model.no-setters`): no builder, no setters, no
  nullable "optional" event; `model.no-setters`

## Example — interface with nested records and a legacy variant

The legacy record is drawn as a separate class here only to keep the two shapes side by side; the note below says where it lives.

```java
…   // the payload value objects are immutable types the context already declares

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.OwnershipUpdated.class, name = "OwnershipUpdated_v2"),
        @JsonSubTypes.Type(value = LegacyEvents.OwnershipUpdatedV1.class, name = "OwnershipUpdated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.OpeningHoursUpdated.class, name = "OpeningHoursUpdated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.LocationUpdated.class, name = "LocationUpdated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.SettingsUpdated.class, name = "SettingsUpdated_v1")
})
public interface DomainEvent {
    record LocationUpdated(String deviceId, Location location) implements DomainEvent {
    }

    record OpeningHoursUpdated(String deviceId, OpeningHours openingHours) implements DomainEvent {
    }

    record OwnershipUpdated(String deviceId, Ownership ownership) implements DomainEvent {
    }

    record SettingsUpdated(String deviceId, Settings settings) implements DomainEvent {
    }
}

class LegacyEvents {

    public record OwnershipUpdatedV1(
            String deviceId,
            String operator,
            String provider) implements DomainEvent {
        DomainEvent.OwnershipUpdated toCurrent() {
            return new DomainEvent.OwnershipUpdated(deviceId,
                    Ownership.of(operator, provider)
            );
        }
    }
}
```

The legacy record is drawn as a separate class above only for readability, and **must not be one** in production: a top-level record
implementing the union violates `event.nested-record`. The legacy record is nested in the persistence adapter that upcasts it, which is
the only nesting the rule allows outside the interface.

Three rules are visible here: the payload is identity + the changed value object, the `_v1` record is kept unchanged forever, and the
upcast is a method on the legacy record that returns the current shape.

## Example — closed union without legacy variants

```java
…   // WorkOrder, Location and BootNotification are immutable records the context already owns

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.InstallationStarted.class, name = "InstallationStarted_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DeviceAssigned.class, name = "DeviceAssigned_v1"),
        @JsonSubTypes.Type(value = DomainEvent.LocationPredefined.class, name = "LocationPredefined_v1"),
        @JsonSubTypes.Type(value = DomainEvent.BootNotificationProcessed.class, name = "BootNotificationConfirmed_v1"),
        @JsonSubTypes.Type(value = DomainEvent.InstallationCompleted.class, name = "InstallationCompleted_v1"),
})
public sealed interface DomainEvent {
    record InstallationStarted(String orderId, WorkOrder order) implements DomainEvent {
    }

    record DeviceAssigned(String orderId, String deviceId) implements DomainEvent {
    }

    record LocationPredefined(String orderId, String deviceId, Location location) implements DomainEvent {
    }

    record BootNotificationProcessed(String orderId, String deviceId,
                                     BootNotification boot, boolean confirmed) implements DomainEvent {
    }

    record InstallationCompleted(String orderId, String deviceId) implements DomainEvent {
    }
}
```

An event may carry the input record that caused it (shown in the union above) because the input is itself an immutable domain record.

The fourth entry shows what `event.name-matches-record` forbids: it registers the subtype name `…Confirmed_v1` for a record named
`…Processed`. The startup check accepts it (it carries a version), but the persisted type no longer names the fact — fix such a pair
with `_v2` rather than by editing the released name.

### The event store

One table per event-sourced aggregate: an `id`, the aggregate identity, `type` (the version-less subtype name), `time` and the
serialised `event` (`jsonb`). `time` is what replay orders by, so it must be `with time zone`, and the replay query needs an index on
`(identity, type, time desc)`. A schema without that index turns every rehydration into a sequential scan.

### Rules specific to a closed union

- Declare the type `sealed` when the context has no legacy variants and every event type is known at compile time; `event.sealed-union`
- Keep the `@JsonSubTypes` list complete — a sealed type with a missing entry fails only at runtime; `event.sealed-complete-subtypes`

## Versioning and legacy events

A startup check in `tools/` parses the version out of the subtype name and refuses to boot without it:

```java
String[] parts = typeName.split("_v");
if (parts.length != 2 || parts[1] == null || parts[1].isBlank()) {
    throw new IllegalArgumentException(
            "Version required in JsonSubTypes name on top of DomainEvent interface, like StationProtocolChanged_v1, "
            + "'_v' part is important, thrown for type name: " + typeName);
}
```

- Introduce `_v(N+1)` only for a breaking payload change; the older record stays in the code, next to the adapter that upcasts it, and
  exposes the upcast as a method returning the current shape (`{LegacyRecord}.toCurrent()`) — there is no shared legacy-event
  interface; `event.breaking-change-new-version`
- Normalise legacy events on read, before they reach the aggregate, through the record's own upcast method
  (`{LegacyRecord}::toCurrent`); `event.upcast-on-read`
- Never mutate or delete an already published `_vN` record — persisted JSON outlives the code; `event.immutable-history`

## Replay

Reconstruction reads the latest event per type and rebuilds the aggregate from it:

```java
…   // the surrounding repository adapter class is elided

@Query(value = """
        select distinct on (type) *
        from device_events
        where device_id = :deviceId
        order by type, time desc""", nativeQuery = true)
List<DeviceEventEntity> findByDeviceId(String deviceId);

…   // the get(...) method of the same adapter

public Optional<Device> get(String deviceId) {
    List<DomainEvent> history = repository.findByDeviceId(deviceId).stream()
            .map(DeviceEventEntity::getEvent)
            .map(event -> event instanceof OwnershipUpdatedV1 legacy ? legacy.toCurrent() : event)
            .collect(Collectors.toList());
    if (history.isEmpty()) {
        return Optional.empty();
    }
    Collections.reverse(history);
    LastEvents events = LastEvents.fromHistoryOf(history);   // history is newest first
    Device device = new Device(deviceId, new ArrayList<>(),
            events.getOrNull(OwnershipUpdated.class, OwnershipUpdated::ownership),
            events.getOrNull(LocationUpdated.class, LocationUpdated::location),
            events.getOrDefault(OpeningHoursUpdated.class, OpeningHoursUpdated::openingHours, OpeningHours.alwaysOpened()),
            events.getOrDefault(SettingsUpdated.class, SettingsUpdated::settings, Settings.defaultSettings())
    );
    return Optional.of(device);
}

record LastEvents(Map<Class<?>, DomainEvent> latest) {

    static LastEvents fromHistoryOf(List<DomainEvent> newestFirst) {
        Map<Class<?>, DomainEvent> latest = new LinkedHashMap<>();
        newestFirst.forEach(event -> latest.putIfAbsent(event.getClass(), event));
        return new LastEvents(latest);
    }

    <E extends DomainEvent, V> V getOrNull(Class<E> type, Function<E, V> read) {
        E event = type.cast(latest.get(type));
        return event == null ? null : read.apply(event);
    }

    <E extends DomainEvent, V> V getOrDefault(Class<E> type, Function<E, V> read, V fallback) {
        V value = getOrNull(type, read);
        return value == null ? fallback : value;
    }
}
```

- Rebuild through the all-args constructor of the aggregate and hand it an empty event buffer; `event.replay-empty-buffer`
- Reduce with last-event-per-type rather than replaying every command; `event.replay-last-per-type`
- When two event *types* carry the same fact (a request and a later postponement, each carrying a date), the merge is decided by each
  row's `time`, not by a fixed type precedence: take the latest dated row of the types that carry the fact. A fixed precedence silently
  resurrects a superseded value on replay; `event.replay-cross-type-order`
- Never re-emit events during reconstruction; `event.replay-no-emit`

## Publishing

The adapter drains, persists and publishes — the write of each event precedes its publication.

```java
…   // inside the repository adapter class

public void save(Device device) {
    List<DomainEvent> emitted = eventsEmittedFrom(device);
    documents.save(documents.findById(device.deviceId)
            .orElseGet(() -> new DeviceDocumentEntity(device.deviceId))
            .setDevice(device));
    emitted.forEach(event -> events.save(new DeviceEventEntity(device.deviceId, event)));

    if (!emitted.isEmpty()) {
        publisher.publishEvent(device.toDeviceConfiguration());
    }
    emitted.forEach(publisher::publishEvent);
}

private static List<DomainEvent> eventsEmittedFrom(Device device) {
    List<DomainEvent> emitted = List.copyOf(device.events);
    device.events.clear();
    return emitted;
}
```

## What to avoid / NOGO

- Do not publish from inside the aggregate or from a service; publishing is the adapter's job after the write; `aggregate.no-io`
- Do not name an event after a command (`Update{Entity}`, `Assign{Entity}`) — events state what happened; `event.past-tense`
- Do not add an event without a version to `@JsonSubTypes`; a union registered in `tools/` fails at startup, an unregistered one fails
  silently at read time; `event.name-version`
- Do not edit or remove a released `_vN` record to avoid upcasting; add `_v(N+1)` and an upcast method on the old record; `event.immutable-history`
- Do not put the aggregate, a JPA entity, or a nullable "old/new pair" in the payload — identity plus the changed value object only; `event.payload-identity-plus-change`; `model.no-persistence-types`
- Do not let the subtype name drift from the record name (a registered `…Confirmed_v1` on a `…Processed` record is exactly that drift); `event.name-matches-record`
- Do not emit an event when the command changed nothing; `aggregate.event-with-change`
- Do not clear the event buffer outside the repository, and never keep a reference to the drained list for later use; `event.drained-by-repository`
- Do not compare an event component of type `Boolean` to a literal in a listener, an upcast or a replay reducer — `if (confirmed)`,
  `Boolean` `null` reads `x != null && x`; the payload is a record, not a flag bag; `model.no-boxed-boolean-compare`

## Enforcement

Evaluated by `sqt check src`:

| Rule | Severity | What it checks |
|------|----------|----------------|
| `event.nested-record` | error | an event record is nested in its `DomainEvent` interface; only a legacy `_v1` record may live beside the persistence adapter that upcasts it (`inside.repository`) |
| `event.name-version` | error | every `@JsonSubTypes` name on the union carries `_v<N>` |
| `model.no-setters` | error | no setter on an event record |
| `model.no-persistence-types` | error | the payload is never a persistence type |
| `model.no-boxed-boolean-compare` | error | an event component of type `Boolean` is never compared to a literal |
| `model.no-framework-annotations`, `model.forbidden-imports`, `model.no-tools-dependency` | error | the model rules apply to every event type and file |
| `aggregate.event-with-change` | warning | a command that changes state also appends an event |
| `aggregate.no-io` | error | the aggregate never publishes — the adapter does |

**Review-only**: `event.past-tense`, `event.payload-identity-plus-change`, `event.immutable`, `event.name-matches-record`,
`event.sealed-union`, `event.sealed-complete-subtypes`, `event.breaking-change-new-version`, `event.upcast-on-read`,
`event.immutable-history`, `event.replay-empty-buffer`, `event.replay-last-per-type`, `event.replay-cross-type-order`,
`event.replay-no-emit`, `event.drained-by-repository`, `event.published-after-save`, `event.snapshot-published`.

Two of those are refused for a stated reason, not merely unautomated:

- `event.sealed-union` — the rule would have to see whether the context carries a legacy variant; a context that carries one keeps its
  union unsealed, so an unconditional `sealed` check would be wrong.
- `event.name-matches-record` — it compares an annotation string with a declaration name; that is cross-element, so it stays a review
  rule.
