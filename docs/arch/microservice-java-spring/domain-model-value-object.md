# Value Object

A value object represent business information and behaviour around. It is an immutable record defined by its values, not by identity.
It carries a piece of business information, and when it has rules, those rules live in its own constructor or factory — never in the caller.
A value object can pe public as a part of shared kernel, or package private kept inside module boundry.

## How to implement

- Represent every value object as a `record`, never as a class with getters and setters; `vo.record`
- Declare a value object used only inside its parent as a nested record of that parent; `vo.nested`
- Give non-trivial construction a static factory named after the business intent, and keep the canonical empty/default value as a static singleton; `vo.factory`
- Put a hard invariant in the compact constructor of the record and enforce it with `if (…) throw …`, so no instance can exist in a
  forbidden state; the throw is the context's own exception, never a JDK type; `vo.invariant-compact-constructor`
- Read the record's **parameters** in that constructor, never its accessors: the fields are assigned only at the end of a compact
  constructor, so `if (isOwned())` there reads the default `null` and the invariant silently never fires; `vo.invariant-reads-parameters`
- Use the two mechanisms for two different defects: `Objects.requireNonNull(x)` for a value the type cannot exist without, and a named `if (…) throw new IllegalArgumentException(…)` for a combination the business forbids (one component set, its pair empty). Do not guard the same case twice; `vo.mandatory-fields`
- Expose derived booleans as methods on the record; `@JsonIgnore` is only meaningful where the mapper serialises getters. The shared mapper grants `FIELD` and `CREATOR` visibility only, so **a method on a package-private record never reaches the wire** — neither with `@JsonIgnore` nor without it; `vo.derived-predicate`
- Read a nullable `Boolean` component as `x != null && x` and answer with the method's return type `boolean`; that is the only null-safe read. A primitive `boolean` is read directly — `if (active)`, `return active`, `!active` — with no comparison at all; `vo.nullable-boolean-read`
- When the same snapshot is **both** persisted and returned to a client, an answer the client must see is a **component**, not a method: a `FIELD`-visibility mapper drops methods, so the derived shape silently disappears from the payload — which is also why a validity method on a flags record never appears in the API; `vo.derived-predicate`
- Make components mandatory by default — all present, `Objects.requireNonNull` where a semantically empty value is illegal; a component is optional only when the business has a named state for its absence (an opening-hours component that is `null` for an always-open site); `vo.mandatory-fields`
- Keep the snapshot free of `public` components — with one sanctioned exception: a component another context reads is `public` and listed in that context's exposed types; `vo.no-public-components-in-snapshot`
- Keep the value object `public` only when another bounded context consumes it (shared kernel), otherwise package-private; `vo.visibility`
- Express variation of the same information as a new instance, produced by a named method (`merge`), never by mutation; `vo.no-mutation`
- Model an open-ended alternative set as a `sealed interface` with one nested record per alternative, so exhaustive `switch` replaces `instanceof` chains; `vo.sealed-alternatives`

## Example

### Business Information

```java
public record Location(
        String street, String houseNumber,
        String city, String postalCode,
        String state, String country,
        Coordinates coordinates) {

    public record Coordinates(BigDecimal longitude,
                              BigDecimal latitude) {
    }
}
```


Nothing but information: no behaviour, no validation.
A nested composite is a piece of its parent (a coordinate pair inside a location).

### Information with a hard invariant

```java
public record Ownership(String operator, String provider) {

    public static Ownership unowned() {
        return new Ownership(null, null);
    }

    public static Ownership of(String operator, String provider) {
        return new Ownership(operator, provider);
    }

    public Ownership {
        if (!isUnowned() && !isOwned()) {
            throw new IllegalArgumentException("ownership must be complete or absent");
        }
    }

    @JsonIgnore
    public boolean isUnowned() {
        return operator == null && provider == null;
    }

    @JsonIgnore
    public boolean isOwned() {
        return operator != null && provider != null;
    }
}
```


### Alternative set of informations with sealed interface

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS)
sealed interface OpeningTime {
    record Opened24h() implements OpeningTime {
    }

    record Closed24h() implements OpeningTime {
    }

    record OpenTime(List<TimeSpan> time) implements OpeningTime {
    }

    record TimeSpan(LocalTime open, LocalTime close) {
    }

    // … closed24h() and opened24h() omitted: the released code has them swapped
    // (closed24h() returns Opened24h and vice versa) — see the NOGO list below

    static OpeningTime opened(int open, int close) {
        return new OpenTime(List.of(new TimeSpan(
                LocalTime.of(open, 0),
                LocalTime.of(close, 0)
        )));
    }
}
```

This nested alternative set is shown as it is in the code, including its `MINIMAL_CLASS` type id — see
`vo.persisted-type-name`: the class name in the stored JSON is a migration debt, not a pattern.

### Information with partial update

```java
@Builder(toBuilder = true)
record Settings(
        Boolean autoStart,
        Boolean remoteControl,
        Boolean billing,
        Boolean reimbursement,
        Boolean showOnMap,
        Boolean publicAccess) {

    static Settings defaultSettings() {
        return builder()
                .autoStart(false)
                .remoteControl(false)
                .billing(false)
                .reimbursement(false)
                .showOnMap(false)
                .publicAccess(false)
                .build();
    }

    public boolean isAutoStart() {
        return autoStart != null && autoStart;
    }

    …   // isRemoteControl(), isBilling(), isReimbursement(), isShowOnMap(), isPublicAccess()

    public Settings merge(Settings other) {
        SettingsBuilder merged = this.toBuilder();
        ofNullable(other.autoStart).ifPresent(merged::autoStart);
        ofNullable(other.remoteControl).ifPresent(merged::remoteControl);
        ofNullable(other.billing).ifPresent(merged::billing);
        ofNullable(other.reimbursement).ifPresent(merged::reimbursement);
        ofNullable(other.showOnMap).ifPresent(merged::showOnMap);
        ofNullable(other.publicAccess).ifPresent(merged::publicAccess);
        return merged.build();
    }
}
```

### Information leaving the Aggregate — immutable snapshot

```java
public record DeviceConfiguration(
        String deviceId,
        Ownership ownership,
        Location location,
        OpeningHours openingHours,
        Settings settings,
        Violations violations,
        Visibility visibility) {
}
```

A snapshot is a value object too: it is assembled by the owner of the state and is immutable afterwards. Same alternative shape on the
process side:

```java
public record InstallationProcessState(String orderId, String deviceId, State state) {
    enum State {PENDING, DEVICE_ASSIGNED, BOOTED, COMPLETED}
}
```
Represent published state as a record that only the state owner constructs;



## What to avoid / NOGO

- Do not write a value object as a mutable class with setters; use a record and a `merge`/factory returning a new instance; `model.no-setters`
- Do not annotate a value object with `@Data`; Lombok `@Builder` is allowed, and `toBuilder = true` is required when the record offers a `merge`; `model.no-lombok-data`
- Do not let a value object reference an aggregate or a repository; a value object is information and behaviour around it; `model.no-model-owner-dependency`
- Do not compare a boolean to a literal — no `Boolean.TRUE.equals(x)`, no `Boolean.FALSE.equals(active)`, no `x == Boolean.TRUE`: they read a flag as a string comparison, they NPE nothing and clarify nothing, and on a primitive their `false` answer hides a lost update instead of showing it. `Objects.equals(…)` is the idiom for **value objects and records**, never for booleans; `model.no-boxed-boolean-compare`
- Do not return `null` from a value object method that answers a question; return a boolean/enum decision; `vo.no-null-answers`
- Do not represent a business decision as a `boolean` pair; introduce an enum with named constants; `vo.decision-as-enum`
- Do not make an internal value object `public` to "allow tests"; tests live in the same package; `vo.visibility`
- Do not widen an internal value object to `public` because a public snapshot exposes it — a snapshot that holds internal
  components is a leak to fix, not a pattern to copy; `vo.no-public-components-in-snapshot`
- Do not enforce an invariant with `assert` — assertions are disabled unless the build enables them (`-ea`), so the object stays constructible in the forbidden state; throw instead; `model.no-assert`
- Do not create two factories whose names do not state the difference (`opened24h()`/`closed24h()` must return what they say); `vo.factory-name-matches-result`
- Do not validate a value object in the aggregate after construction; validation belongs to the value object's constructor or factory; `vo.validation-in-vo`

## Enforcement

Encoded in `.agents/sqt.yaml`, evaluated by `sqt check src`:

| Rule | Severity | What it checks |
|------|----------|----------------|
| `vo.invariant-compact-constructor` | error | a compact constructor throws or rejects its components |
| `vo.factory` | hint | a record with four or more components offers a named static factory — a Lombok `@Builder` record already has one |
| `model.no-setters`, `model.no-lombok-data`, `model.no-assert` | error | the model rules cover every value object |
| `model.no-boxed-boolean-compare` | error | no `Boolean.TRUE/FALSE.equals(x)`, no `x == Boolean.TRUE/FALSE` — a nullable `Boolean` reads as `x != null && x`, a primitive reads as `x` |
| `model.no-model-owner-dependency`, `model.no-persistence-types`, `model.forbidden-imports` | error | a value object holds no repository, no entity and no framework type |

The value-object role itself is a classifier: `is.value-object` = a model type that is **not** an aggregate, policy, command,
event record, event union, transport object or framework-annotated class. It can only match a record, an enum or an
interface.

**Refused, with the reason** — these four are not merely unautomated:

- `vo.record`, `vo.no-mutation` — `sqt 0.1.0` ignores `files:`/`ignores:` on a check and an ast-grep rule has no path
  predicate, so a `class` cannot be attributed to the value-object role: the classifier would also catch shared utility types in
  `tools/` and a test double. A rule that fires on fakes and utilities is worse than no rule. The
  mutable-field part is covered by `model.no-setters` for model classes.
- `vo.mandatory-fields` — which components are semantically optional is business knowledge: a component is `null` for a named
  business state (always open, absent in this request).
- `vo.nested` — "used only inside its parent" is usage analysis, not syntax.
- `vo.no-null-answers` — the shape cannot be separated from a read model: an `ofNullable(...)` factory returns `null` by design
  when the projection has not seen the aggregate yet.

**Review-only**: `vo.derived-predicate`, `vo.factory-name-matches-result`, `vo.sealed-alternatives`, `vo.decision-as-enum`,
`vo.validation-in-vo`, `vo.visibility`, `vo.no-public-components-in-snapshot`.

- Do not introduce `@JsonTypeInfo(use = Id.MINIMAL_CLASS)` for a persisted alternative set: the stored JSON then carries the
  Java class name, so renaming an alternative silently breaks every stored document. Use `Id.NAME` with explicit subtypes,
  the same way domain events are versioned; `vo.persisted-type-name`. A `MINIMAL_CLASS` id already in a schema is a migration
  to plan, not a pattern to copy
- Do not answer a `vo.factory` hint by adding an alias factory — if the only construction paths are Jackson's canonical
  constructor and persistence rehydration, the record legitimately has no factory, and an alias factory that just calls
  the canonical constructor exists only to silence a hint. A hint is closed by a decision, recorded in review; `vo.factory`

Tightening a value object tightens the HTTP contract that carries it: a `PATCH` that used to accept `{"address": {"city":
"X"}}` is rejected at deserialisation (`400`) once the record's compact constructor demands a complete address. That is the
intended direction — but it is a client-visible change, so it belongs in the same commit as the invariant.
