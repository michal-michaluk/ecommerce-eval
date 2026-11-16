# ArchUnit Tests

The dependency rules of every bounded context are executable. One class — the shared ArchUnit description class — holds the
predicates and the six rule factories; one file per context, `ArchitectureOf{Context}Test`, instantiates those six rules with its own
package and its own two allow-lists. Nothing else in the test tree names a dependency rule — that is what keeps "verify ArchUnit
constraints remain valid" a mechanical step.

## Classification — the input every rule reads

Three name-derived predicates plus one package-derived predicate:

| Predicate | Definition |
|---|---|
| `adapters` | simple name ends with `Controller`, `Repository`, `Projection`, `Entity`, `Integration`, `Listener` or `Client` |
| `services` | simple name ends with `Service` |
| `model` | neither of the above — every other class, record, interface and enum |
| `mediators` | resides in `..mediators..` — a package, not a name |

Consequence: a wrong suffix silently removes a class from every rule. `{Concept}Editor` is model, `{Aggregate}Repository` is an
adapter, `{Area}Projection` is an adapter, `{Command}` is model. This is why `structure.role-suffix` is a hard rule and not a style
preference.

## The six rule factories

| Factory | Selects | Forbids |
|---|---|---|
| `adaptersDependencies(pkg, used)` | adapters in `pkg` | depending on anything except `used`, `pkg` itself, `<root-package>.tools..`, and anything **outside** the parent package (libraries) |
| `servicesDependencies(pkg, used)` | services in `pkg` | depending on anything except `used`, `pkg`, `java..`, `lombok..`, `org.springframework.stereotype`, `…transaction.annotation`, `…context.event` |
| `modelDependencies(pkg, used)` | top-level model types in `pkg` | depending on anything except model types, `used`, `java..`, `lombok..`, `jakarta.validation..`, `com.fasterxml.jackson..` |
| `adaptersIsolation(pkg)` | adapters in `pkg` | being accessed by any class outside `pkg` |
| `servicesIsolation(pkg, allowed)` | services in `pkg` | being accessed by anything except `pkg`, `..mediators..`, `allowed` |
| `modelIsolation(pkg, exposed)` | model in `pkg`, minus `exposed` | being accessed by any class outside `pkg` |

Two details are load-bearing:

- `modelDependencies` adds `.and().areTopLevelClasses()`, so a nested `static class …Entity` inside an adapter is never
  checked against the model whitelist — the adapter's own rule covers it.
- `adaptersDependencies` derives the package parent with `parentOf(aPackage)` and allows everything outside it, which is
  how an adapter may use Spring, Hibernate and Jackson without listing them.

`servicesIsolation` always receives the description class's `mediators` predicate as the extra allowed user, which is the only
sanctioned way one context reaches another context's service (`mediator.no-direct-service-call`).

## The two allow-lists

Every context test declares exactly two predicates, and they mean opposite things:

| Predicate | Meaning | Who lists what |
|---|---|---|
| `sharedKernelExposed` | types **of this context** that another context may touch — subtracted from `modelIsolation` | the producer lists its own exports |
| `sharedKernelUsed` | types **of another context** this context may touch — added to all three dependency rules | the consumer lists what it imports |

A shape to start from:

| Context test | `sharedKernelExposed` | `sharedKernelUsed` |
|---|---|---|
| the producer's | the snapshot, its value objects, a command the primary port takes | — |
| the consumer's | — | the producer's exported types it imports |
| a context with no test | nothing is exposed, and nothing is checked | — |

`belongToAnyOf` covers nested classes: an entry for `{Concept}` also matches a nested `{Concept}.{Part}`. Listing the nested type
explicitly anyway is harmless, but redundant, so a nested type needs no separate entry.

## Example — the context test template

Copy this file and change everything the concrete names stand for: the root package, the class name, `PACKAGE`, and the two lists. The
fence below shows one context of one root package, with every type it names declared in the header comment.

```java
// the types the two lists name, declared so the example stands alone:
//   record Ownership(boolean isUnowned) { }                        // this context's value object
//   record Location(String street, String city) { }                // this context's value object, with nested Coordinates
//   record DeviceConfiguration(String deviceId, Ownership ownership, Location location) { }   // this context's snapshot
//   record UpdateDevice(String deviceId, String operator) { }      // command the primary port takes
//   record BootNotification(String deviceId, Instant at) { }       // record imported from another context
@AnalyzeClasses(
        packages = "devices.configuration",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureOfDeviceContextTest {
    public static final String PACKAGE = "devices.configuration.device";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
            UpdateDevice.class, DeviceConfiguration.class,
            Ownership.class, Location.class
    );
    public static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf(
            BootNotification.class
    );

    @ArchTest
    public static final ArchRule adaptersDependencies = ArchitectureDescription.adaptersDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    public static final ArchRule servicesDependencies = ArchitectureDescription.servicesDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    public static final ArchRule modelDependencies = ArchitectureDescription.modelDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    public static final ArchRule adaptersIsolation = ArchitectureDescription.adaptersIsolation(PACKAGE);

    @ArchTest
    public static final ArchRule servicesIsolation = ArchitectureDescription.servicesIsolation(PACKAGE, ArchitectureDescription.mediators);

    @ArchTest
    public static final ArchRule modelIsolation = ArchitectureDescription.modelIsolation(PACKAGE, sharedKernelExposed);
}
```

- Put the test in the context package and name it `ArchitectureOf{Context}Test`, so it is found next to the code it protects; `archunit.context-test-location`
- Keep the class `public` and the six rule fields `public static final` — ArchUnit discovers `@ArchTest` fields reflectively; `archunit.static-rule-fields`
- Declare `PACKAGE` once as a constant and pass it to all six factories; a literal repeated six times is one edit away from six different
  rules; `archunit.package-constant`
- Always analyse from the root package with `ImportOption.DoNotIncludeTests` — analysing the context package alone hides a
  violation that comes from a sibling; `archunit.analyze-root`
- Instantiate all six rules in every context, even when the layer is empty, and add `.allowEmptyShould(true)` to that rule instead of
  deleting it — a context with no `*Service` does exactly this for `servicesDependencies` and `servicesIsolation`; `archunit.six-rules-always`
- Keep the rule fields named after the factory, so a reader can tell which rule failed from the report; `archunit.rule-field-name`

## How to add a context (the procedure)

1. Add the six rules with `PACKAGE = "<root-package>.{context}"` and both lists empty; `archunit.new-context-empty-lists`
2. Run it. Every foreign type the new context touches appears as a violation — that list **is** the dependency you are about to create; `archunit.list-is-the-edge` (reviewed)
3. Add each of those foreign types to `sharedKernelUsed` of the new test, and to `sharedKernelExposed` of the producing context's test;
   `boundary.exposed-type-list`
4. If the context has sub-packages of its own (a protocol family), decide the `PACKAGE` pattern explicitly: a `PACKAGE` of `a.b.c`
   selects only that package, while a `..` suffix (`"a.b.c.."`) also includes `a.b.c.protocols.v1`. Choose, write the choice in the
   test, and do not rely on the default; `archunit.package-pattern-explicit`
5. Add the new context's own `*Test`, `*Fixture` and `*Assert` in the same package; `test.package-private`

Step 4 is the one decision a copy-paste cannot make, and the two variants fail differently:

- With a narrow `PACKAGE` and both lists empty, every real edge shows up as a violation: `adaptersDependencies` on an adapter that
  uses a foreign model type, `servicesDependencies` on a primary port that holds an event publisher, `servicesIsolation` on a
  sub-package adapter that calls a service the pattern protects, `modelIsolation` on a protocol adapter that constructs a foreign event;
  `archunit.list-is-the-edge`
- With the `..` variant and both lists filled, `servicesDependencies` can still fail on the same event publisher: the services
  whitelist allows `org.springframework.context.event` (where `@EventListener` lives) and not `org.springframework.context` (where
  `ApplicationEventPublisher` lives). That is a real defect, not a list to extend — publishing from a primary port contradicts
  `integration.publish-in-adapter`, and until it is settled the context has no enforceable architecture test; `integration.publish-in-adapter`
- A violation that survives both lists is never a reason to widen a whitelist: fix the code, or add the edge in two tests; `archunit.no-weakening`

## What the rules do not cover

- **Field types inside a method body** — `onlyDependOnClassesThat` reads declared dependencies (signatures, annotations, supertypes, field
  types), which is enough for this style of code, but a locally constructed class still shows up as a dependency; `archunit.dependency-graph` (reviewed)
- **The validation package in the model whitelist** — the list allows `jakarta.validation..`, so a model record may carry `@Valid`,
  `@NotNull` or `@Size` on a component; a codebase still on Java EE allows `javax.validation..` instead, never both by accident. Adding
  an annotation package is a deliberate edit of the list in the same commit, proven by a model type that uses it; `archunit.model-whitelist-explicit`
- **`tools/` direction** — no rule stops `tools` from importing a context; a fully-qualified reference is still a dependency and no
  import-based rule sees it (`utils.no-context-dependency`)
- **What an adapter may not inject** — a controller that injects a `*Repository` of its own package is compliant as far as ArchUnit is
  concerned; that one is an `sqt` warning (`http.no-adapter-dependency`)

## Verifying and adding rules

```sh
./gradlew test --tests "<root-package>.*.ArchitectureOf*ContextTest"
```

- Run all context tests together after any change to the description class, because one factory edit changes all contexts at
  once; `archunit.test-after-factory-edit`
- Never weaken a rule to make code compile: no deleting a rule, no widening `sharedKernelUsed` without the matching consuming edge, no
  `allowEmptyShould(true)` on a rule whose set is not actually empty; `archunit.no-weakening`
- When a rule fires on a real violation, fix the code (rename the type, add the missing port); do not add the
  offending type to a list; `archunit.no-weakening`
- Keep the description class a pure static factory set: no state, no configuration, no per-context branching inside it; `archunit.description-single-source` (reviewed)

## What to avoid / NOGO

- Do not write a rule inline in a context test — a second copy of `classes().that(...)` is a rule no other context obeys; `archunit.description-single-source` (reviewed)
- Do not classify by annotation (`@RestController`, `@Repository`) — the suffix is the single classifier and both docs and rules read it; `structure.role-suffix`
- Do not add a marker interface or an empty base class to make a rule match; `archunit.no-marker-interface` (reviewed)
- Do not add a context without its `ArchitectureOf{Context}Test` — that is a testing NOGO; `archunit.context-test-location`
- Do not silence a violation by listing a foreign type you do not actually need; the list is the published edge; `boundary.exposed-type-list`

## Enforcement

The classifier and the six rules are enforced — by ArchUnit, in `./gradlew test`, not by `sqt`. The `archunit.*` ids above are
review rules and checklist items: they describe how to write and extend the test, which no static check over production code can verify.
The `sqt` config holds the complementary families (`aggregate.*`, `vo.*`, `event.*`, `policy.*`, `model.*`, `http.*`), which cover the
shapes ArchUnit cannot express.
