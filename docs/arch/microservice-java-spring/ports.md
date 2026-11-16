# Ports

A port is the only place where a context states what it offers and what it needs, without naming who provides it. Three
kinds exist, told apart by who implements them and who may see them:

| Kind | Written by | Implemented by | Who may see it | Example |
|------|-----------|----------------|------------|---------|
| **Primary port** | the context that owns the use case | the same context's service | `public` — other contexts call it through a mediator | `{Area}Service` |
| **Secondary port** | the context that needs to store something | a persistence adapter in the same package | package-private — `structure.port-visibility` | `{Aggregate}Repository` |
| **Contract port** | the **consumer** context, in the consumer's language | the consumer itself, a mediator, or the provider's own component | `public` — it is the cross-context seam | `{Consumer}Query` |

The direction is fixed: a primary port takes and returns model types (`{Command}`, `{Snapshot}`), a secondary port
moves the aggregate and its snapshot, and a contract port is written in the consumer's business language so the provider can be
replaced without touching the consumer.

## How to implement — primary port

- Name the primary port `{Area}Service` and make it `public`; the suffix is the classifier the architecture test reads; `port.primary-name`
- Annotate it `@Service` and `@Transactional` at class level — the service, not the aggregate and not the controller, owns the transaction boundary; `port.primary-transactional`
- Mark every read method `@Transactional(readOnly = true)` so a query never opens a write transaction; `port.primary-transactional`
- Constructor-inject ports and process-wide infrastructure only — a secondary port (`{Aggregate}Repository`), a contract port
  (`{Consumer}Query`), the `Clock` — never an adapter class, never a `*Repository` implementation, and never an
  `ApplicationEventPublisher` (publishing is an adapter concern, `integration.publish-in-adapter`); `port.primary-injects-ports`
- Take and return the aggregate snapshot (`{Snapshot}`) or a value object, never the aggregate itself, never a JPA entity, never an editor; `port.primary-model-signature`
- Read the aggregate, apply the command, save, and return the snapshot — the service holds the lifecycle, not the rules; `port.primary-lifecycle`
- Keep the port body to the lifecycle: one `get`, one `apply`, one `save`, one `toSnapshot` per use case, and no branching on business state; `port.primary-thin` (`function_statements` warning > 5, error > 10; `method_logical_lines` warning > 15, error > 30)
- Publish nothing from a primary port — the adapter publishes after it has written; `port.primary-no-publish` (`aggregate.no-io` covers the aggregate side)
- Reach one secondary port and any number of contract ports; a second secondary port means the use case spans two aggregates and belongs to a mediator or a new context; `port.primary-single-secondary` (reviewed)

## How to implement — secondary port

- Name it `{Aggregate}Repository`, keep it package-private with the adapter that implements it; `structure.port-visibility`
- Declare the smallest lifecycle a persistence strategy must support: `Optional<{Editor}> get(String id)` and `void save({Editor} editor)` — a strategy that cannot express a method cannot be swapped; `port.secondary-lifecycle`
- Return the **aggregate**, not its snapshot: the adapter rehydrates it and `save` receives it, which is what lets one port carry several storage shapes; `port.secondary-aggregate-signature`
- Return `void` from `save` — whether the aggregate also produces a read model or events is the adapter's decision; `port.secondary-void-save`
- Keep persistence types out of the signature: no `*Entity`, no `EntityManager`, no Spring Data interface; `port.secondary-no-persistence-types`
- When the stored value *is* a configuration document rather than an aggregate, let the port return the value object directly
  (`{ConfigDocument}Repository.get()` → `{ConfigDocument}`) instead of inventing a one-field wrapper; `port.secondary-value-signature`
- Add no per-use-case query method (`findBy{Field}`) — a read that needs a query goes to a projection, not to the write port; `port.secondary-no-query-methods`
- Keep one secondary port per aggregate and per swap axis; a second port over the same aggregate is a second strategy with no consumer; `port.secondary-one-per-aggregate` (reviewed)

## Contract ports

A *contract port* is an interface one context declares so another can answer it. It is a port like the other two, but its design space —
consumer-owned vocabulary, event vs port, mediator or not, using another context's public value objects — is the subject of
the context-integration rules, which own `contract.*`, `mapping.*` and `mediator.*`. Keep this file's rules for the two in-context ports
and use those for anything that crosses.

- Primary and secondary ports stay inside one context; a contract port is the **only** port that crosses; `mediator.no-direct-service-call`
- Name the method after the question the consumer asks (`rateFor`, `get`), and return a value the consumer can use unchanged; `port.contract-consumer-question`

## The fake double — the test seam the port buys

A secondary port exists so the use case can be tested without a database. The fake is an inner class of the test that needs it,
named `Fake*`/`Stub*`/`Mock*`/`Dummy*`/`InMemory*`, backed by a plain `Map`, and it implements the port **and nothing else**.

```java
class OrderServiceTest {

    final Map<String, OrderEditor> orders = new HashMap<>();
    final OrderService service = new OrderService(new FakeOrderRepository());

    class FakeOrderRepository implements OrderRepository {
        @Override
        public Optional<OrderEditor> get(String orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public void save(OrderEditor order) {
            orders.put(order.orderId, order);
        }
    }
}
```

- Name a test double `Fake*`, `Stub*`, `Mock*`, `Dummy*` or `InMemory*` — the prefix is the classifier, so a collaborator named
  `Repo` cannot be told from a production type by a rule; `test.fake-naming`
- Keep the fake inside the test class that uses it, unless a second test needs it; then it moves next to the fixture, never into
  production sources; `test.fake-local`
- Back the fake with a `Map` or a list and mirror the port's storage semantics (find-or-create on save) so the test exercises the
  same lifecycle the adapter will; `test.fake-mirrors-port`
- Assert on the snapshot, never on the fake's fields: the custom assert is the only assertion seam a port test needs; `test.custom-assert`
- Cross a context in a test through the same seam: a fake contract port replaces the mediator, so a consumer test never needs the
  provider's Spring beans; `test.fake-contract-port`
- Never test a service against the real adapter — that is an integration test and belongs to the adapter's own test class; `test.unit-no-container`

## Example — a use case behind its port

The controller knows the port's owner, the owner knows the port, and the port knows neither.

```java
@Service
@Transactional
public class OrderService {

    private final OrderRepository repository;

    OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> getOrder(String orderId) {
        return repository.get(orderId)
                .map(OrderEditor::toSnapshot);
    }

    public Optional<OrderSnapshot> updateOrder(String orderId, UpdateOrder update) {
        return repository.get(orderId)
                .map(order -> {
                    update.apply(order);
                    repository.save(order);
                    return order.toSnapshot();
                });
    }
}

interface OrderRepository {
    Optional<OrderEditor> get(String orderId);
    void save(OrderEditor order);
}
```

The `Optional` in the port is the whole "not found" vocabulary: the service passes it on, the controller turns it into `404`, and
no layer invents an exception type (`http.not-found-status`).

## Example — the same port, several strategies

The port never changes when the strategy does. Which one is live is a wiring decision (`@Primary`), not a port decision —
`persistence.primary-strategy`. One strategy per storage shape:

| Strategy | How it stores the aggregate |
|---|---|
| Document snapshot | one JSONB column |
| Document + history | JSONB column + one row per emitted event (the `@Primary` strategy) |
| Event sourcing | event rows only, replayed on read |
| Normalizing | relational columns + child rows |

## What to avoid / NOGO

- Do not inject an adapter where a port belongs: a controller that injects a `*Repository` leaves the write path with no primary
  port at all, which is the gap this section exists to prevent; `port.primary-for-every-adapter` (held at `warning`)
- Do not let a controller or a projection reach a `*Repository`: the controller calls the primary port, the projection owns its own
  read table; `http.no-adapter-dependency`
- Do not put a business rule, a branch or a mapping decision in a service beyond the lifecycle — invariants stay in the aggregate
  (`aggregate.command-method`), decisions in a policy (`policy.no-inline-decision`); `port.primary-thin`
- Do not grow a secondary port per screen or per query; `port.secondary-no-query-methods`
- Do not make a secondary port `public` "for a test" — the test lives in the same package, so package-private already reaches it;
  `structure.port-visibility`
- Do not put anything but a value object in a contract signature — not the provider's editor, entity, policy, repository or service, and not
  your own aggregate either; `contract.value-objects-only`
- Do not return the aggregate across a port that another context calls — expose the snapshot and keep the editor package-private;
  `structure.editor-package-private`
- Do not add a setter-shaped port (`void setName(String id, String name)`) — that is a field write across a boundary, not a use
  case; `port.secondary-lifecycle`

## Enforcement

Encoded in the `scope:` bands of `.agents/sqt.yaml`: `port.primary-thin` — `function_statements` warning > 5, error > 10 and
`method_logical_lines` warning > 15, error > 30 on methods inside a `*Service` and on controller handlers.

The remaining `port.*` ids and the two `test.*` bullets this file borrows are proposals as well. `structure.port-visibility` is
drafted in the structure rules; the `contract.*`, `mapping.*` and `mediator.*` ids belong to the context-integration rules.

Cheap to encode as shape checks: `port.primary-name`, `port.primary-transactional`, `port.secondary-lifecycle`,
`port.secondary-no-persistence-types`, `test.fake-naming`, `test.fake-local`. The direction rules
(`port.primary-injects-ports`, `port.primary-for-every-adapter`) belong to the dependency graph, where the access selectors already
exist (`in.repository`, `in.controller`, `is.controller`, `in.tests`).
