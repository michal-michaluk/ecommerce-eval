# Code Structure

One Gradle module, one Spring context, **one package root**, and one package per bounded context directly under it. Inside a context
package there are no technical sub-layers — no `domain/`, `application/`, `infrastructure/` — the stereotype is carried by the file
name, and the platform is carried by the annotation. Two packages sit outside the contexts: `tools/` (technical utilities shared by
everyone) and `mediators/` (the only place where one context reaches another).

## Package Layout & Bounded Contexts

```
src/main/java/<root-package>/
    package-info.java                  # bare `package <root-package>;`
    <EntryPoint>Runner.java            # the one @SpringBootApplication entry point

    <business-context-1>/              # bounded context: owns a write model
    <business-context-2>/              # bounded context: owns a read model
    <business-context-n>/              # one package per context, no nesting
    mediators/                         # the only package that names two contexts
    tools/                             # technical utilities, business-blind
```

**Package root:** `<root-package>`. **Entry point:** one runner class at the root carrying `@SpringBootApplication`, `@EnableRetry`,
`@EnableScheduling`, `@EnableJpaRepositories(considerNestedRepositories = true)`, `@ConfigurationPropertiesScan`.

- One package per bounded context, named after the business area in one lowercase word, directly under the root; `structure.context-package`
- Do not nest technical layers inside a context — a `<context>/domain/` or `<context>/adapter/` folder destroys the flat reading order and splits one concept over three packages; `structure.flat-context`
- Nest a wire protocol only as `<context>/protocols/<version>/` — the only sub-packages that exist, one per protocol version; `structure.protocol-subpackage`
- Keep exactly one `@SpringBootApplication` at the root; configuration flags for the whole module live on it, never on a context; `structure.entry-point`
- Add a bounded context as a new sibling package — never as a sub-package of an existing one, so the ArchUnit per-context test can name it by package; `structure.sibling-context`

### Example — a context that owns a write model

Everything the aggregate lifecycle needs, and nothing else: the aggregate, its snapshot, its events, the two ports, one or more
persistence adapters and one HTTP adapter.

```
<root-package>/<business-context-1>/
    <Concept>Editor.java               # aggregate (write model) — package-private
    <Concept>.java                     # aggregate snapshot record — public if another context reads it
    DomainEvent.java                   # event union: interface + nested records
    <Concept>Repository.java           # secondary port — the only storage contract
    <Strategy>Repository.java          # persistence adapter, one per storage strategy
        (+ nested @Entity and nested Spring Data interface)
    <Area>Service.java                 # primary port — the aggregate lifecycle, @Transactional
    <Area>Controller.java              # HTTP adapter, package-private
    <ValueObject>.java                 # records the aggregate is made of
```

### Example — a context that owns a read model

No aggregate, no repository port: an event handler writes one denormalized table, and the HTTP adapter reads only that.

```
<root-package>/<business-context-2>/
    <Area>Projection.java              # @Component with one @EventListener per consumed record
        (+ nested @Entity holding the read columns and nested Spring Data interface)
    <ReadModel>.java                   # one record per endpoint shape, with an ofNullable(...) factory
    <Area>Controller.java              # HTTP adapter, package-private, injects the projection
```

### Context shapes you will meet

The two examples above are the extremes; most contexts are one of these four:

| Shape | What it owns | Extra |
|---|---|---|
| write model (example 1) | aggregate, editor, snapshot, events, two ports, persistence adapters, one HTTP adapter | several interchangeable persistence strategies behind one `@Primary` adapter |
| policy only | a configurable calculation behind a service, stored as a configuration document | no aggregate, no secondary port |
| projections + protocol adapters | event handlers plus adapters under `protocols/<version>/`; no aggregate, no secondary port | consumes another context's snapshot, asks a third one through a port |
| read model (example 2) | one denormalized table written by event handlers; the HTTP adapter reads only that | consumes events from several producers |

A `protocols/<version>/` package holds adapters **inside** a context, not contexts of their own: they translate a wire format and call
the context's `{Area}Service`.

## Stereotypes & Naming Conventions

The role of a file is its suffix — that is the vocabulary the shared ArchUnit description class reads, so a wrong suffix silently
removes the class from every rule.

| Stereotype | Name | Modifier |
|---|---|---|
| Aggregate — editor | `{Concept}Editor`; the bare noun is the snapshot | package-private |
| Calculation policy | business noun + `Calculation`, no aggregate suffix | package-private |
| Aggregate snapshot | bare business noun, no suffix | `public` when another context reads or returns it |
| Value object | business noun, no suffix | package-private unless it crosses |
| Domain event | `DomainEvent` interface + nested records | `public` |
| Command | intent noun, no suffix | `public` when the primary port takes it |
| Primary port | `{Area}Service` | `public` |
| Secondary port | `{Aggregate}Repository` | package-private when implemented in-context |
| Contract port | business noun | `public` — it is consumed by another context |
| Persistence adapter | `{Strategy}Repository` | package-private |
| HTTP adapter | `{Area}Controller` | package-private |
| Read model | business noun | package-private |
| Projection | `{Area}Projection` | package-private |
| JPA entity | `*Entity`, **nested** in the adapter that owns it | package-private static class |
| Mediator | `{Consumer}To{Provider}Mediator` | package-private |
| Util | `*Configuration`, `*Types`, business noun | `public` when contexts use it |

- Name a class after the business concept it represents; never add `Aggregate`, `ValueObject`, `Dto` or `Impl`; `structure.business-name`
- Give adapters and services a role suffix (`*Controller`, `*Repository`, `*Projection`, `*Service`, `*Mediator`), because the suffix is the only classification input; `structure.role-suffix`
- Keep one top-level type per file, with the file named exactly after it; helper types are nested; `structure.one-type-per-file`
- Nest a JPA entity and its Spring Data repository interface inside the adapter that owns them, so a storage shape never becomes a top-level neighbour; `structure.nested-entity`
- Do not create a `*Dto`, `*Payload` or `*Model` type — a request is a command, a response is a snapshot or a read model; `structure.no-dto`

## Public only with an edge

The modifier states one thing: is this type part of a contract, or is it internal? `package-private` is the default and carries
no obligation; `public` means something outside the context may depend on it, and that dependency must be one of the edges the
architecture test allows.

| Public because… | Types |
|---|---|
| it is the entry point | the root `*Runner` |
| it is a primary port another context may call | `{Area}Service` |
| it is a contract port implemented outside the context | `{ContractPort}` |
| it is a model type another context reads | `{Concept}`, `{ValueObject}`, `{Event}`, `DomainEvent` |
| the primary port signature uses it | `{Command}` |
| it is a shared util | `{Util}Configuration`, `{Registry}Types`, `{Utility}` |

Everything else — aggregates, editors, model records, all resource adapters, all projections, every entity, every mediator, every
secondary port — is package-private.

- Default every new type to package-private, and widen it only when a specific edge requires it; `structure.package-private-default`
- Make a type `public` only together with the edge that consumes it — the same PR adds the type to the consuming context's ArchUnit allow-list; `structure.public-needs-edge`
- Keep a resource adapter (`*Controller`) and a projection package-private even when they are Spring beans — the bean type is never the contract; `structure.adapter-visibility`
- Keep a secondary port package-private when the adapter lives in the same package; make it `public` only when another context implements it; `structure.port-visibility`
- Keep the aggregate editor package-private — it is the write model, and no adapter outside the context may hold it; `structure.editor-package-private`
- Rely on the `ObjectMapper`'s `ANY` field visibility, configured once in `tools/`, when a package-private record is an API payload; do not make a model type `public` just to serialise it; `structure.no-public-for-json`
- Keep a bean that implements a public contract package-private when Spring resolves it by interface — a projection implementing `{ContractPort}` and a mediator implementing `{ContractPort}` are both package-private and injected as the interface; `structure.bean-needs-no-public`
- Let the implemented method be `public` even though the class is package-private: an override may not narrow the interface's visibility, and a public method on an invisible class leaks nothing; `structure.override-visibility`

The `structure.no-public-for-json` rule is what lets a `{Area}Controller` return a package-private calculation record and accept it as a
`@RequestBody` — the mapper is configured for field and creator visibility `ANY`, so a type needs no getters and no `public`
modifier to be a wire contract.

## Shared Utils: `tools/`

`tools/` is **not** a shared kernel. A shared kernel is a jointly owned domain model; `tools/` holds no business language — no
aggregate, no value object, no domain event — only the technical plumbing the contexts would otherwise copy. It is a shared
*utility* package, and the direction is one-way: a context may depend on `tools/`, `tools/` may not depend on a context.

| Util | What it provides | Why it is shared |
|---|---|---|
| the JSON configuration | one static `ObjectMapper` (`ANY` visibility, unknown properties ignored) exposed as a bean, plus the startup event-type registry | every context serialises, several persist |
| the JSON format mapper | a Hibernate `FormatMapper` bridging JSONB columns to that mapper | wired once for the whole persistence layer |
| the event-type registry | a static registry `Class → Type(type, version)` parsed from `@JsonSubTypes` names | the event store writes `type` from it |
| the last-event reducer | a last-event-per-type reducer over an event history | event-sourcing replay |
| the feature store | a JSONB key-value store with typed `as(Class)` access | any context needs configurable documents |
| the clock configuration | one `Clock.systemUTC()` bean | time is injected, never read statically |

- Keep `tools/` free of business terms — if a util mentions an order, an invoice or a device, it belongs to that context; `utils.no-domain-vocabulary`
- Depend on `tools/` freely from adapters, services and beans (`tools/` is in the adapters' allow-list); `utils.context-may-use`
- Never let `tools/` reference a context type, not even fully qualified — the dependency inverts the whole package layout and no import-based rule sees it; `utils.no-context-dependency`
- Keep model code free of `tools/`: an aggregate or value object receives the value, it never asks a util for it; `utils.not-in-model` (encoded as `model.no-tools-dependency`)
- Register a util as a bean or a `@Configuration`, and keep the one deliberate exception — a static registry initialised from a `@Configuration`'s `@PostConstruct`; `utils.bean-registration`
- Keep a util generic — a feature store holds *any* document; a util that knows one context's schema is that context's adapter; `utils.generic`

## Inter Context Integration: `mediators/`

`mediators/` is the only package that names two contexts. Every way a context may reach another — event, contract port implemented in
the consumer, contract port implemented by a mediator, port for a snapshot, port for a command — is specified by the integration
patterns, together with the port-declaration styles and the mediator rules (`mediator.*`).

Two facts belong here because they are structural: the package is the role (the shared ArchUnit description class recognises
`resideInAPackage("..mediators..")`), and no context may depend on `mediators/` in a way that lets a mediator back into the model —
the direction is `context → contract interface`, `mediator → provider service`, never the reverse.

- Reach another context only through a contract port and a mediator — never by injecting a sibling `*Service`; enforced by `servicesIsolation` with the `mediators` allowance, `mediator.no-direct-service-call`
- Decide the integration pattern before adding an edge — it decides which of the two mechanisms the edge uses; `mapping.preference-order`

## What to avoid / NOGO

- Do not add technical sub-packages inside a context (`domain/`, `app/`, `infra/`, `api/`); the flat package plus suffixes is the structure; `structure.flat-context`
- Do not introduce a `*Impl`, `*Dto`, `*Payload` or `*Aggregate` suffix; `structure.role-suffix`
- Do not make a type `public` "for later" or to solve a serialisation problem; `structure.public-needs-edge`
- Do not put a JPA entity or a Spring Data repository at the top level of a context; nest it in the adapter; `structure.nested-entity`
- Do not add a second `@SpringBootApplication`, `@ComponentScan` or `@ConfigurationPropertiesScan` in a context package; `structure.entry-point`
- Do not let a public class become the injection contract — the interface is the contract, the bean type is an implementation detail; `structure.bean-needs-no-public`
- Do not let `tools/` mention a business type — the shared utils stay business-blind; `utils.no-domain-vocabulary`
- Do not call a sibling context's `*Service`, `*Repository` or model type directly from a service, controller or projection; add a contract port and, if needed, a mediator; `mediator.no-direct-service-call`
- Do not let a mediator grow responsibilities — it is a wire, not a component with behaviour; `mediator.no-logic`

## Enforcement

The vocabulary in this document is what the architecture tests consume, so most of it is checked — by ArchUnit, not by `sqt`:

| Fact | Enforced by |
|---|---|
| adapters/services/model classification by suffix | `adapters`, `services`, `model` in the shared ArchUnit description class |
| a mediator is "anything in `..mediators..`" | `mediators` in the same description class |
| an adapter depends only on its own context, `tools/` and libraries | `adaptersDependencies` in the same description class |
| a service is reached only from its context or a mediator | `servicesIsolation` in the same description class |
| model code never references `tools/` | `model.no-tools-dependency` in the `sqt` dependency rules |

`structure.*`, `utils.*` and `mediator.*` are proposals for the materialisation phase (except `mediator.body-size`, a metric band in the
`sqt` config); the visibility and package rules are cheap to encode as `sqt` checks, the direction rules
(`utils.no-context-dependency`) belong in the dependency graph.
