# Context Boundaries

A bounded context is one package directly under `<root-package>`, and two contexts talk **only** through the four patterns in this
file. The boundary is not prose: it is the pair of allow-lists in each context's `ArchitectureOf{Context}Test`, so
adding an edge means editing two tests, and that edit is the review gate. This file covers the context map, the mapping patterns, how
to declare a port to another context, and the mediators that implement the two on-demand patterns.

## The abstract map

```
  <business-context-A>  ── event (record) ──────────────▶  <business-context-B>
          ▲                                                       │
          │  contract port + mediator (on demand)                 │  contract port implemented
          └───────────────────────────────────────────────────────┘  inside B (own data)
                    │
                    └── port + mediator ──▶  <business-context-C>   (command or snapshot)

  <root-package>/mediators/   the only package that names two contexts
  <root-package>/tools/       technical only, one-way: a context may use tools, tools never names a context
```

Two mechanisms, and no third: a **published record** (an in-process event) or a **contract port** (an
interface the *consumer* declares, implemented either by the consumer itself or by a mediator). A direct call from one context's class
to a sibling's `*Service`, `*Repository` or model type is neither — `servicesIsolation` makes it fail for services and `modelIsolation`
plus the exposed list makes it fail for model types.

## An example map

The same two mechanisms, written as context roles rather than as a type inventory:

| Edge | Pattern | What crosses |
|---|---|---|
| `<business-context-A>` → `<consumer-context>` | event `{Concept}` | `{Concept}`, `{ValueObject}` |
| `<business-context-A>` → `<read-model-context>` | event `{Concept}` | `{Concept}`, `{ValueObject}`, a nested type of it |
| `<producer-context>` → `<read-model-context>` | events `{ProducerEvent}`, `{StatusEvent}` | `{ProducerEvent}`, `{StatusEvent}` |
| `<producer-context>` → `<calculation-context>` | contract port `{Answer}`, implemented by a mediator that calls `{Area}Service` | `{ConsumerEvent}` in, `Duration` out |
| any context → `tools/` | plain dependency | `{Util}`, `{Registry}`, `Clock` bean |

Read the first three rows as "the consumer lists these in its `sharedKernelUsed`; the producer lists what it exports in its
`sharedKernelExposed`" — each context's test holds both lists verbatim. A context without a test of its own has no import list, and the
records it exports are then checked only from the consumers' side.

An edge has one publisher and one listener side: a record a projection consumes needs a production publisher, or the edge is a
half-wire that no test can see.

Read each context by what it owns, not by its size: one owns the write model and publishes its snapshot; one owns the wire protocols,
consumes a snapshot and asks another context for a value; one owns a configurable calculation and answers through a mediator; one owns
read models and consumes the producers' events. Exposing a command record is optional, and worth it only when a consumer must invoke
that use case.

## Pattern 1 — listen on an event and change own state

The consumer keeps a small fact of its own, derived from the producer's record — for example whether an `{Entity}` is still active.

```java
// package <root-package>.<consumer-context>
// the producer's exposed snapshot, as the consumer sees it:
//   record DeviceConfiguration(String deviceId, Ownership ownership) { }
//   record Ownership(boolean isUnowned) { }
// the consumer's own types, shown as signatures:
//   enum State { UNKNOWN, EXISTING }
//   class KnownDeviceEntity                                    // nested @Entity
//           KnownDeviceEntity(String deviceId)
//           KnownDeviceEntity state(State state)
//   interface KnownEntityRepository                            // nested Spring Data interface
//           Optional<KnownDeviceEntity> findById(String id)
//           KnownDeviceEntity save(KnownDeviceEntity entity)

@EventListener
public void handle(DeviceConfiguration event) {
    if (event.ownership().isUnowned()) {
        put(event.deviceId(), State.UNKNOWN);
    }
}

// own field: private final KnownEntityRepository entities;
private void put(String deviceId, State state) {
    entities.save(entities.findById(deviceId)
            .orElseGet(() -> new KnownDeviceEntity(deviceId))
            .state(state));
}
```

- Keep the reaction a local write: read the payload, write your own table, return; `mapping.event-own-state`
- **Enforce no invariant on the listener side** — never throw, never reject the producer's fact, never "correct" it. The listener runs
  inside the producer's transaction (`integration.listener-joins-transaction`), so a throw rolls back the producer's successful write;
  a payload the consumer cannot use is handled as data (ignore the row, store the default), never as an error; `mapping.listener-no-invariant`
- Ask the producer for nothing — no repository, no service, no second query; the record must carry everything; `integration.listener-local-only`
- Make the write idempotent and order-independent: `findById(...).orElseGet(...)` then a full state assignment, so a re-publication
  rewrites the same row; `projection.idempotent-recompute`
- Name the consumer's own type in its own language (`{Consumer}Lookup.State`), not the producer's; `contract.own-value-objects`

## Pattern 2 — listen on an event and merge into a read model

The consumer keeps a denormalized row per `{Concept}`, rebuilt from several producers' records.

```java
// package <root-package>.<read-model-context>
// the producer's exposed snapshot, as the consumer sees it:
//   record DeviceConfiguration(String deviceId, Ownership ownership) { }
//   record Ownership(boolean isUnowned) { }
// the consumer's own types, shown as signatures:
//   class DeviceReadsEntity                                    // nested @Entity, own @Table
//           DeviceReadsEntity(String deviceId)
//           DeviceReadsEntity setOwnership(Ownership ownership)
//           DeviceReadsEntity setDetails(DeviceConfiguration details)
//           DeviceReadsEntity setPin(DevicePin pin)
//           DeviceReadsEntity setSummary(DeviceSummary summary)
//           List<String> getStatuses()
//   record DevicePin(String status, String summary)
//           static DevicePin ofNullable(DeviceConfiguration details, List<String> statuses)
//   record DeviceSummary(String status, String summary)
//           static DeviceSummary ofNullable(DeviceConfiguration details, List<String> statuses)
//   interface DeviceReadsRepository                            // nested Spring Data interface
//           Optional<DeviceReadsEntity> findById(String id)
//           DeviceReadsEntity save(DeviceReadsEntity entity)

// own field: private final DeviceReadsRepository reads;
@EventListener
public void handle(DeviceConfiguration details) {
    DeviceReadsEntity entity = reads.findById(details.deviceId())
            .orElseGet(() -> new DeviceReadsEntity(details.deviceId()));

    entity
            .setOwnership(details.ownership())
            .setDetails(details)
            .setPin(DevicePin.ofNullable(details, entity.getStatuses()))
            .setSummary(DeviceSummary.ofNullable(details, entity.getStatuses()));

    reads.save(entity);
}
```

- One handler per record type, and every handler recomputes the derived views from the stored row, so `{Payload} → {BootEvent} → {StatusEvent}`
  and `{StatusEvent} → {BootEvent} → {Payload}` end in the same row; `projection.order-independent`
- Copy the payload into your own column instead of pointing at the producer's table — a read model survives the producer being down; `boundary.copy-not-pointer`
- Keep the read model's own table and schema; `projection.own-table`
- The mechanics are synchronous and in-transaction (see the publishing and listening rules below); the read-model shape is the projection rules' — `projection.handler-thin`, `projection.idempotent-recompute`; `mapping.event-read-model`

## Pattern 3 — port for reading another context's snapshot (port + mediator)

The consumer needs the producer's current state **at request time**, so an event-driven copy is wrong (it may be stale) or too large
(whole aggregates). The consumer declares a port that returns the producer's *exposed* snapshot type, and a mediator delegates to the
producer's primary port. The template:

```java
// consumer's contract — <root-package>.<consumer-context>
// the producer's exposed snapshot:
//   record DeviceConfiguration(String deviceId, Ownership ownership) { }
public interface DeviceOfRecord {
    Optional<DeviceConfiguration> currentConfiguration(String deviceId);
}

// <root-package>.mediators
@Component
@AllArgsConstructor
class SearchToDeviceMediator implements DeviceOfRecord {

    private final DeviceService devices;   // the producer's primary port:
                                           //   Optional<DeviceConfiguration> getDevice(String deviceId)

    @Override
    public Optional<DeviceConfiguration> currentConfiguration(String deviceId) {
        return devices.getDevice(deviceId);
    }
}
```

- Return the producer's snapshot type (`{Concept}`) — it is already in the producer's `sharedKernelExposed`, so this adds
  the consumer's name to a list, not a new type to the system; `mapping.port-snapshot`
- Return `Optional` for "not found" and let the consumer decide the wire status; the mediator adds no status logic; `mediator.no-logic`
- Accept the question as an id or a small value object of the consumer's language, never a query object of the producer's; `contract.client-driven`
- Use this pattern sparingly: a synchronous read couples the consumer to the producer's availability. If a copy can be kept fresh, use
  pattern 2; `boundary.snapshot-over-query` (reviewed)

## Pattern 4 — port for dispatching a command to another context (port + mediator)

The consumer must make the producer *do* something — start a process, register a fact, change state the consumer does not own. The
consumer declares a command-shaped method, the mediator delegates, and the producer's own service decides everything. The template:

```java
// consumer's contract — <root-package>.<consumer-context>
// the command the producer accepts, and the type its service acts on, shown as signatures:
//   record CommissioningRequest(String deviceId, Instant at)
//           Commissioning start()
//   record Commissioning(String deviceId, Instant at) { }
public interface StartCommissioning {
    void startCommissioning(CommissioningRequest request);
}

// <root-package>.mediators — the producer's service owns the use case and the invariants
//   class CommissioningService                                  // producer's primary port
//           void start(Commissioning commissioning)
@Component
@AllArgsConstructor
class DevicesToCommissioningMediator implements StartCommissioning {

    private final CommissioningService commissionings;

    @Override
    public void startCommissioning(CommissioningRequest request) {
        commissionings.start(request.start());
    }
}
```

- Pass a command the producer can accept, or a value object both sides already own; the mediator maps nothing beyond parameter passing; `mapping.port-command`
- Keep the call one-directional: the producer does not know the consumer, does not call back and publishes nothing specifically for it.
  If the producer must report the outcome, that is pattern 1 — the consumer listens to the producer's normal event stream; `mapping.one-direction`
- Let the producer's service return the decision (an id, a snapshot, or nothing) — the mediator never invents a result; `mediator.no-logic`
- Do not model a command as an event and expect a rejection: an event cannot refuse and its publisher never learns the outcome; `mapping.event-cannot-refuse` (reviewed)

## Declaring a contract port — value objects only

The port lives in the **consuming** context's package and is written in the consumer's language (`mediator.contract-in-consumer`).
Whatever its style, **its signature is made of value objects and nothing else** — on both sides of the edge.

| Allowed in a contract signature | Forbidden in a contract signature |
|---|---|
| a value object of the consumer's (`{ConsumerEvent}`, `{Consumer}Lookup.State`) | the producer's **aggregate** or editor (`{Concept}Editor`) |
| a `public` value object the producer exposes (`{Concept}`, `{ValueObject}`) | the producer's **entity**, persistence document or repository (`{Entity}`, `{Aggregate}Repository`) |
| a JDK value (`String`, `Duration`, `Instant`, `Optional<…>` of a value object) | the producer's **service**, policy, projection or controller (`{Area}Service`, `{Business}Calculation`) |
| an enum or a closed record of the consumer's, when the answer is a decision | the **consumer's own** aggregate, editor or entity — a port is not a way to move your write model into a collaborator |
| a command-shaped value object the producer accepts (`{Command}`) | a `*Repository` or `*Service` of either side, in a parameter or a return type |

- Keep every component and every return type a value object — a record, an enum, a JDK value, or `Optional`/`List` of those; `contract.value-objects-only`
- Note what the compiler already stops: the producer's editor, its entities and its secondary ports are **package-private**, so a foreign
  context cannot import them at all. What the compiler cannot stop is a `public` type — a `*Service`, or a projection someone made public —
  and that is exactly what `contract.value-objects-only` checks in the static rules; `contract.value-objects-only`
- Never name the producer's aggregate, editor, entity, policy, projection or service in the signature — those are its internals, and the
  producer's `sharedKernelExposed` can never list them; `contract.no-producer-internals`
- Never name the consumer's own aggregate or editor in the signature either: the port is a seam between two collaborators, not a channel
  for your write model; pass a value object, or a command record the producer owns the rules for; `contract.no-consumer-internals`
- Treat "the provider's service" as an *implementation* detail of the mediator, not part of the contract: the mediator may hold
  `{Area}Service` in a field, but no signature may mention it; `mediator.implements-contract`
- Four styles then decide *whose* value objects the signature uses:


### 1. Client-driven contract, balanced with the shared kernel — the preferred style

The consumer states the question in the vocabulary it already has, and takes back a value it can use unchanged: it asks for a
`Duration` given its own record.

```java
// the whole contract — package <root-package>.<consumer-context>
// the consumer's own record: record BootNotification(String deviceId, Instant at) { }
// the result is a JDK value: Duration
public interface HeartbeatInterval {
    Duration heartbeatIntervalFor(BootNotification boot);
}
```

- Declare the port from the consumer's need (client-driven), not from the provider's API: the method name is the consumer's question,
  the parameter is the consumer's record, the result is a type both sides already understand (`Duration`); `contract.client-driven`
- Balance the shared kernel: a small, stable type on each side — the consumer's input record in, a JDK value out — so neither context
  gains a dependency on the other's model for free; `contract.client-driven`
- Give the port one question; a second question is a second port, so a consumer can adopt one without the other; `contract.single-question` (reviewed)

### 2. Prefer your own public value objects in the contract you declare

Whatever the consumer names in its own port, it must be willing to publish — here the port answers with an enum the consumer owns.

```java
// the whole contract — package <root-package>.<consumer-context>
public interface KnownDevices {
    enum State {UNKNOWN, EXISTING}

    State get(String deviceId);
}
```

- Name the result in your own vocabulary (the `{Outcome}` enum), not the provider's (`{ValueObject}`, a status string); the consumer then
  changes representation without renegotiating the port; `contract.own-value-objects`
- Keep the port's types `public` and inside the consumer's package — a contract type another context must reference cannot be
  package-private; `mediator.contract-public`
- Prefer a closed set (enum, sealed record) over a string or a boolean for an outcome, so the consumer's `switch` is exhaustive; `policy.decision-as-value`

### 3. Use another context's public value objects when the contract must carry them

If the answer *is* the producer's snapshot or one of its value objects, use that type instead of inventing a look-alike. The
producer's type is `public` and listed in its `sharedKernelExposed`; the consumer lists it in `sharedKernelUsed`.

```java
// consumer's contract — a read port that must answer with the producer's own snapshot
// producer's exposed types: record Ownership(boolean isUnowned) { }
//                          record DeviceConfiguration(String deviceId, Ownership ownership) { }
public interface DeviceOfRecord {
    Optional<DeviceConfiguration> currentConfiguration(String deviceId);
}
```

- Reach for the producer's exposed type when the consumer genuinely needs that shape; the arch test then carries one entry, and the two
  sides cannot drift; `contract.foreign-value-object`
- List the foreign type on **both** sides — `sharedKernelUsed` in the consumer, `sharedKernelExposed` in the producer — in the same
  commit as the code, never "for later"; `boundary.exposed-type-list`
- A nested type needs no separate entry: `belongToAnyOf({Concept}.class)` already covers a nested value object, so an extra entry is
  harmless redundancy, not a requirement; `boundary.nested-type-covered`
- Do not use a producer type you cannot publish: an editor, an entity or a policy never enters a contract; `structure.editor-package-private`

### 4. Never copy a value object from another context

```java
// consumer's package — WRONG: a local twin of the producer's exposed snapshot
record DeviceData(String deviceId, String operator, String provider, String street, String city) {}
```

- A local copy is a second schema with no compiler between them: the producer adds a field, the copy silently drops it, and
  `modelIsolation` cannot see the relationship because the copy is yours; use the exposed type (style 3) or carry only the fields your
  own language actually names; `contract.no-local-copy`
- Do not re-declare a foreign enum (`{ValueObject}.State`, a status string) to avoid a dependency — a rejected dependency becomes an
  unowned mapping table; `contract.no-local-copy`
- The one legitimate copy is a **read model**: a projection stores the producer's payload in its own column (`pattern 2`), and that row
  is storage, not a type; `projection.copy-of-foreign-payload`

## Mediators — implementing patterns 3 and 4

A mediator is the only component allowed to name two contexts. It implements the consumer's contract port and delegates to the
provider's primary port.

```
<consumer-context>                          mediators                        <producer-context>
  {ContractPort}  ◀── implements ──  {Consumer}To{Provider}Mediator  ──calls──▶  {Area}Service
  (contract port)                     (@Component, package-private)                (primary port)
  {Consumer}Service
  (injects {ContractPort}; its own {Dependency}, publisher)
```

```java
// package <root-package>.mediators — the whole class
// the two sides it joins, shown as signatures:
//   interface HeartbeatInterval                                  // the consumer's contract port
//           Duration heartbeatIntervalFor(BootNotification boot)
//   class DeviceService                                          // the producer's primary port
//           Duration calculateInterval(BootNotification boot)
//   record BootNotification(String deviceId, Instant at) { }      // the consumer's own record
@Component
@AllArgsConstructor
class ProtocolsToIntervalsMediator implements HeartbeatInterval {

    private final DeviceService devices;

    @Override
    public Duration heartbeatIntervalFor(BootNotification boot) {
        return devices.calculateInterval(boot);
    }
}
```

| Fact | Enforced by |
|---|---|
| the mediator lives in `<root-package>.mediators` | the shared ArchUnit description class — `resideInAPackage("..mediators..")` |
| a service may be reached from outside its context **only** by a mediator | `servicesIsolation(pkg, mediators)` in that description class |
| the consumer names no sibling context | its own `sharedKernelUsed` list (`boundary.exposed-type-list`) |

- Put every cross-context implementation in `<root-package>.mediators` — the role is the *package*, not the name; `mediator.package`
- Name the class `{Consumer}To{Provider}Mediator` after the two contexts it joins; `mediator.name`
- Make the mediator a package-private `@Component` and the contract `public`: Spring injects by interface, so the class never needs to be
  visible; `mediator.contract-public`, `mediator.package-private`
- Constructor-inject exactly one provider **primary port** (`{Area}Service`) as a private field — never a repository, an adapter, a
  projection or an aggregate; the field is implementation, the contract signature stays value-objects-only; `mediator.implements-contract`
- Implement one contract with one method whose body is one delegation — one statement; bound `function_statements`
  warning > 2, error > 4; `mediator.single-method`, `mediator.body-size`
- Keep no field other than the provider port, no cache, no `Clock`, no configuration store; `mediator.no-state`
- Reach exactly one provider; two providers means two mediators, so each can be removed independently; `mediator.single-provider`
- Never chain a mediator into another mediator, never listen (`@EventListener`), never publish — a mediator answers a question; `mediator.no-chain`, `mediator.no-listener`, `mediator.no-publish` (reviewed)
- Keep the consumer holding the interface only — `private final {ContractPort} port;`, never `{Area}Service`; `mediator.consumer-holds-interface`
- Have exactly one implementation per contract, so the consumer's behaviour cannot depend on bean ordering; `mediator.single-implementation` (reviewed)

### When not to write one

| Question | Pattern | Cost |
|---|---|---|
| the consumer can keep a copy the producer publishes | pattern 1 or 2 | one table in the consumer |
| the answer is a projection of data the consumer already receives | contract port implemented **inside the consumer** by its own `{Area}Projection` | none — no new bean |
| the answer needs the producer's calculation, or its state at request time | mediator (pattern 3 or 4) | one bean, one hop, one more place to change |

- Prefer the event; prefer the consumer-owned contract implementation; write a mediator last; `mapping.preference-order`
- Remove the mediator when the answer becomes available in the consumer's own projection — `mediator.removal-when-projection` (reviewed)
- Keep a mediator only for data another context owns: a mediator that answers a question about the consumer's own data is exactly what
  `mediator.only-across-contexts` forbids
- Write a mediator only for a calculation or a live read/command — a pure copy is an event; `mediator.existence-justified` (reviewed)
- Test the mediator through the contract port, with a fake port on the consumer side; do not write a test that only proves delegation; `mediator.test-through-contract` (reviewed), `mediator.test-delegation-worthless` (reviewed)
- A mediator with no test through its port is untested, and the missing test is the missing proof that the consumer works with a fake; `mediator.untested`

## Boundary checks to run on your own project

- Every context has its own `ArchitectureOf{Context}Test`; a context without one has no enforced import list, and its exports are then
  checked from the consumers' side only; `boundary.missing-context-test`
- `tools/` must not name a context type — a fully-qualified reference is still a dependency and no import-based rule sees it; `utils.no-context-dependency`
- Patterns 3 and 4 stay templates until a consumer needs a live read or a command; their rules prescribe, they do not describe running
  code; `mapping.port-snapshot`, `mapping.port-command`

## What to avoid / NOGO

- Do not import a sibling context's model type, aggregate, repository or adapter — `modelIsolation` fails it unless the producer listed
  the type; `boundary.exposed-type-list`
- Do not inject a sibling `*Service` into a service, controller or projection; add a contract port and, if needed, a mediator; `mediator.no-direct-service-call`
- Do not enforce an invariant in a listener — it runs in the producer's transaction and a throw rolls back a write that succeeded; `mapping.listener-no-invariant`
- Do not answer a synchronous need with an event, or a copyable fact with a mediator; `mapping.preference-order`
- Do not put an `if`, a mapping or a default in a mediator: a mediator with a branch is a policy in the wrong package; `mediator.no-logic`
- Do not copy a producer's value object into your package; use the exposed type or keep the copy as a projection row; `contract.no-local-copy`
- Do not let an aggregate, an editor, an entity, a policy, a service, a repository or a projection appear in a contract signature — on
  either side of the edge; a port that carries one is a leaked write model; `contract.value-objects-only`
- Do not let `tools/` mention a business type — the shared utils stay business-blind; `utils.no-domain-vocabulary`
- Do not create a second crossing mechanism for an edge that already has one: one edge, one wire; `boundary.one-wire-per-edge` (reviewed)
- Do not make a context's internals `public` to solve a boundary problem; widening visibility is not crossing; `structure.public-needs-edge`

## Enforcement

Two mechanisms: the per-context ArchUnit allow-lists (one suite of the six rules per context) and `sqt` dependency rules
(`model.no-tools-dependency`, `model.forbidden-imports`, and `mediator.no-direct-service-call` via `servicesIsolation`). Two things are
materialised as `sqt` artifacts: the check `contract.value-objects-only` (`error`, on every `public interface` whose parameter or return
type ends in `Editor|Entity|Service|Repository|Projection|Controller|Calculation|Decision`) and the metric band `mediator.body-size`
(`function_statements` 2 / 4 on `*Mediator` methods). The remaining `mapping.*`, `contract.*`, `mediator.*` and `boundary.*` ids are
review rules: they describe which edit is legal, which no shape check can decide.
