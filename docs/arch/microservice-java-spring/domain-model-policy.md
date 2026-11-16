# Policy

A policy is a business calculation or decision, represented as a first class citizen (as a class) and tested independently.
Both the input to a calculation or decision and its result are important model pieces, and should be well represented as immutable
value objects. A value object from the existing model can be used as parameter or result; in some cases a dedicated value object can
be designed (a projection of information from other value objects or from the aggregate) to simplify the internal implementation or to
make the policy independent from other models.

Multiple shapes of policy are possible; pick by the business nature of the calculation or decision, not by taste:

| Shape | Recognise it by | Evolution nature |
|-------|-----------------|------------------|
| **Static policy** | Stateless formula, no variants, pure function, easily used in multiple places like value objects or aggregate methods, cannot be mocked for testing | changed only with code changes |
| **Instance policy** | Stateless or stateful, no variants, can be configurable from params read out of storage, can be injected into a constructor of services, adapters, value objects or the aggregate, can be mocked for testing | dynamic pick of configuration |
| **Strategy Pattern** | Multiple variants of a calculation or decision with the same input and output, instance is picked and injected into the caller's constructor | dynamic pick of variant |
| **Rule Engine** | Many individual rules composed, often selected or parametrised by users and stored as decision tables, an internal DSL / specification etc. | dynamic selection of rules |
| **Read Model backed policy** | The policy requires a significant amount of data accumulated in time, so the data needs to be precomputed for performance | based on dynamic data |

A complex policy can be expressed as a mix of **Rule Engine** with multiple **Strategy Pattern**, some configurable **Instance policy**
and a **Read Model backed policy**.

## How to implement

- Name a configurable rule set `*Calculation` and a static outcome `*Decision`, or put a `calculate*`/`decide*` method on the value object
  or aggregate that owns the result; `is.policy`
- Only a `*Calculation` declares a fallback: it holds rules that can be absent, so it owns `default…()`; a `*Decision` is the outcome of a
  fixed computation and has nothing to default; `policy.explicit-default`
- Type the input as an immutable domain record the context already owns or as a published contract record of another context, never as a
  transport DTO and never as raw storage JSON. Flags (`boolean usable`) are legitimate inputs — a decision is often a function of two
  booleans; `policy.domain-input`
- Keep the policy free of `Optional` returns and of exceptions — it answers, it does not fail; `policy.no-exceptions`
- Declare the fallback explicitly as a field or factory (`defSeconds`, `defaultRules()`, `default{Value}()`); `policy.explicit-default`
- Keep the policy type package-private; a policy another context needs becomes `public` deliberately and is added to that context's
  exposed list — there is no automatic exemption; `policy.visibility`
- Return the decision as a value the caller can publish — enum constant, record, `Duration`; `policy.decision-as-value`
- Carry no size bound: a calculation accumulates rules by design, so a statement or line ceiling either pushes the rule table into data a
  reader cannot follow or splits one precedence chain across two classes. Its shape is held by `policy.precedence-in-data`,
  `policy.no-io`, `policy.no-exceptions` and `policy.decision-as-value` instead. If a bound is ever wanted, the reference point is the
  generic `function_statements: error: 20` that `sqt init` writes, never a measurement of existing code; `policy.no-size-bound`
  (reviewed — deliberate absence)

A policy is model code, so the model-isolation rules apply to it: no framework or persistence type, no repository/service field, no setter
(`model.forbidden-imports`, `model.no-persistence-types`, `model.no-setters`).

## Example — Static policy method

```java
record Visibility(boolean roamingEnabled, ForCustomer forCustomer) {

    static Visibility calculateVisibility(boolean usable, boolean showOnMap) {
        return new Visibility(usable, ForCustomer.calculateForCustomer(usable, showOnMap));
    }

    enum ForCustomer {
        USABLE_AND_VISIBLE_ON_MAP, USABLE_BUT_HIDDEN_ON_MAP, INACCESSIBLE_AND_HIDDEN_ON_MAP;

        private static ForCustomer calculateForCustomer(boolean usable, boolean showOnMap) {
            if (!usable) {
                return ForCustomer.INACCESSIBLE_AND_HIDDEN_ON_MAP;
            } else if (showOnMap) {
                return ForCustomer.USABLE_AND_VISIBLE_ON_MAP;
            } else {
                return ForCustomer.USABLE_BUT_HIDDEN_ON_MAP;
            }
        }
    }
}
```

Here the static policy method is embedded in the value object that represents the policy result, so the type carries no fallback and no
`default…()`: the decision is fixed, only its inputs vary. `usable` is the caller's answer to "may this thing be exposed at all", which
the aggregate computes from its own quality report (a validity flag combined with an exposure flag); the policy does not decide it.

## Example — Rule Engine with configurable instance policies

```java
record BootNotification(String deviceId, String vendor, String model, String firmware) {
}

record HartbitIntervalCalculation(
        List<DeviceIdRule> byIds,
        List<ModelRule> byModel,
        int defSeconds) {

    static HartbitIntervalCalculation defaultRules() {
        return new HartbitIntervalCalculation(List.of(), List.of(), 1800);
    }

    …   // the factories that build the two rule lists

    Duration calculateInterval(BootNotification boot) {
        return Duration.ofSeconds(Stream.of(byIds, byModel)
                .flatMap(Collection::stream)
                .filter(rule -> rule.matches(boot))
                .findFirst()
                .map(Rule::seconds)
                .orElse(defSeconds));
    }

    interface Rule {
        boolean matches(BootNotification boot);

        int seconds();
    }

    record DeviceIdRule(int seconds, SortedSet<String> devices) implements Rule {
        @Override
        public boolean matches(BootNotification boot) {
            return devices.contains(boot.deviceId());
        }
    }

    record ModelRule(int seconds, String vendor, Pattern model, Pattern firmware) implements Rule {
        @Override
        public boolean matches(BootNotification boot) {
            return Objects.equals(vendor, boot.vendor())
                   && model.matcher(boot.model()).matches()
                   && (firmware == null || firmware.matcher(boot.firmware()).matches());
        }
    }
}
```

The configuration itself is loaded by the service through a port and stays outside the policy. Rules are stored as one JSON document
under a stable key in a feature-flagged configuration port, and the patterns are persisted as strings and compiled in the rule
factories — a `java.util.regex.Pattern` is not a storage type. The service call is three lines and holds no rule:

```java
…   // inside the service that answers the interval request; repository is the port it injects

public Duration calculateInterval(BootNotification boot) {
    HartbitIntervalCalculation rules = repository.get();
    return rules.calculateInterval(boot);
}
```

A `*Decision` is a **total** function of its inputs: every combination maps to a named outcome, and the last branch is a
default rather than a fallthrough — an unhandled combination that returns `null` is a defect, not a decision.

Two answers from one rule set are two entry points, not two types to invent: the rule set answers the amount
(`calculateSurcharge(...)`) and, when a second consumer only needs a label, either the same type exposes a second
`calculate…` method or a `*Decision` is derived from the same inputs. Do not copy the rules into the second type.

Precedence is the order of the collections the policy holds, never a property of a rule: the stream reads `byIds` before
`byModel`, and inside `byModel` a rule carrying a firmware pattern must be listed before the generic model rule, because
`findFirst()` takes the first match. An authoring UI that reorders rules reorders the decision.

## What to avoid / NOGO

- Never build a policy as a `@Service`, `@Component` bean; `policy.pure`
- Do not read a repository, a `Clock`, `Instant.now()`, a system property or a configuration utility inside a policy; pass the value in; `policy.no-io`
- Do not throw from a policy to signal an unmet condition; report it as a value; `policy.no-exceptions`
- Do not return `boolean` where the business has named outcomes — introduce the enum; `policy.decision-enum`
- Do not grow a command method with a decision table or a calculation; move it to a named policy. A single precondition
  that rejects one contradictory request is not a policy — keep it as an `ensure…` method on the aggregate; `policy.no-inline-decision`
- Do not duplicate the fallback in several places; define it once next to the policy; `policy.explicit-default`
- Do not let precedence depend on iteration order of a `Set`/`HashMap`; use `List`, `LinkedHashMap` or `SortedSet` — an
  ordered set is fine, a hashed one is not. This applies to record components as much as to fields; `policy.precedence-in-data`
- Do not compare a policy input to a boolean literal — `if (!usable)`, never `Boolean.FALSE.equals(usable)`: a policy reads its inputs
  as truth values (the example above), and a literal comparison hides a null or a missing input behind a silent `false`; `model.no-boxed-boolean-compare`

## Enforcement

Evaluated by `sqt check src`. `is.policy` is the classifier: a type that is **not** an adapter, a service, an aggregate, a command, an
event, a transport object or a framework-annotated class, and that either is named `*Calculation`/`*Decision` or declares a
`calculate*`/`decide*` method (the capital letter is required, so `calculateInterval` classifies and `calculate` alone does not).

| Rule | Severity | What it checks |
|------|----------|----------------|
| `policy.pure` | error | no `@Service`/`@Component`/`@Repository` on a policy |
| `policy.no-io` | error | no repository, service, clock or configuration field, no `@Autowired`, no `now()`/`currentTimeMillis()`/`getProperty()` call |
| `policy.no-exceptions` | error | a `calculate*`/`decide*` method never throws |
| `policy.precedence-in-data` | error | precedence lives in ordered collections, never in a `Set`/`Map` field |
| `policy.explicit-default` | error | a `*Calculation` type declares a static `default…()` factory; a `*Decision` is exempt by name |
| `policy.decision-as-value` | error | `calculate*`/`decide*` returns a value, never `void` and never a bare `boolean` |
| `policy.visibility` | error | a policy stays package-private |
| `model.no-boxed-boolean-compare` | error | a policy input is read as a truth value, never compared to a `Boolean` literal |

`policy.explicit-default` is scoped to the `*Calculation` name, not to `is.policy`: a value object that matches `is.policy` through its
`calculate…` method has no fallback to declare, and a static decision has none either.

**Deliberately unmeasured**: `policy.no-size-bound` — no `function_statements` / `method_logical_lines` band is configured for a policy
method. The reference bound to adopt if one is ever wanted is `sqt init`'s generic `function_statements: error: 20`.

**Refused, with the reason**: `policy.domain-input` — a policy that takes primitives or flags is legitimate, so an `error` rule would
forbid the static-policy example this document prescribes.

**Review-only**: `policy.decision-enum`, `policy.no-inline-decision`.
