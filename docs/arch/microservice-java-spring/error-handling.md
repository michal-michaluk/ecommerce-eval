# Error Handling

A context refuses work in exactly one way: it throws **one unchecked exception**, declared in one file, carrying an **error type**
that owns the message and the **domain values** a caller needs. No per-error exception classes, no checked exceptions, no messages
built at the throw site, no caller matching on a string.

The mechanism is per bounded context and covers every kind of failure that context can raise — a refused command, a value object
whose invariant does not hold, an unreadable stored document, a failed integration, a lost concurrent write. It says nothing about
how a transport renders a failure: the HTTP mapping and the adapter retry policy are separate playbooks.

## The single file

```java
package <root-package>.<context>;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The only exception this context raises. */
class OrderException extends RuntimeException {

    enum Error {
        OWNERSHIP_INCOMPLETE("an order must be owned or unowned"),
        COMMAND_VALUE_MISSING("%s is required for this command"),
        QUANTITY_NOT_POSITIVE("quantity must be positive, got %s"),
        DOCUMENT_UNREADABLE("stored document %s could not be read"),
        INTEGRATION_FAILED("%s did not answer"),
        CONCURRENT_MODIFICATION("order %s was changed by someone else");

        private final String template;

        Error(String template) {
            this.template = template;
        }

        String format(Object... details) {
            return template.formatted(details);
        }
    }

    private final Error error;
    private final List<Object> details;

    OrderException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    OrderException(Error error, Throwable cause, Object... details) {
        super(error.format(details), cause);
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    Error error() {
        return error;
    }

    List<Object> details() {
        return details;
    }
}
```

That is the whole mechanism: one class per context, one nested enum, a message template per constant, an immutable detail list, and a
cause-taking constructor for integration failures.

## How to implement

- Declare exactly one exception per bounded context, named `{Context}Exception`, in the context package, package-private like every
  other internal type; `error.single-per-context` (reviewed)
- Extend `RuntimeException` — a domain refusal is not a recoverable checked condition, and a checked type per error is exactly what
  this mechanism replaces; `error.unchecked-per-context`
- Nest the error type as `enum Error` inside the exception, and put the **only** message for that error in the enum constant's
  constructor argument; the throw site passes the type and the values; `error.type-enum-nested`, `error.message-in-enum` (reviewed)
- Name the constant after the condition the business refuses (`QUANTITY_NOT_POSITIVE`, `OWNERSHIP_INCOMPLETE`), never after a class
  or a table; the enum is the context's error vocabulary and a caller may switch on it; `error.type-names-condition` (reviewed)
- Pass the offending values as **details** — the identity, the rejected value, the source name — so a caller, a log or a transport can
  render them without parsing the message: `new OrderException(Error.QUANTITY_NOT_POSITIVE, quantity)`; `error.details-are-values` (reviewed)
- Keep the detail list immutable and null-tolerant: a missing value is itself the detail (`null` means "absent"), so the constructor
  copies with `Arrays.asList` and wraps with `unmodifiableList`, never `List.of`; `error.details-immutable`
- Offer exactly two constructors: `(Error, Object…)` for a domain refusal and `(Error, Throwable, Object…)` for a failure caused by an
  integration, a serializer, or the database; the cause stays attached and is never re-wrapped into a message; `error.cause-on-integration`
- Throw it from the aggregate when a command contradicts the state, from a value object when its invariant does not hold, and from an
  adapter when the outside world misbehaves — the same type in all three places; `error.domain-throws-context-type` (aggregates
  enforced; value objects and adapters reviewed)
- Read the **parameters** in a record's compact constructor, never its accessors: fields are assigned only at the end of that
  constructor, so `if (isOwned())` there always sees the default `null`; `vo.invariant-reads-parameters`
- Let an adapter translate, not decide: an unreadable document or a non-success answer becomes the context exception with a cause; the
  adapter adds no retry, no fallback and no second exception type; `error.adapter-translates-only` (reviewed)
- Keep the exception free of framework and transport types — no status codes, no `ResponseEntity`, no HTTP client type; a transport maps
  the `Error` values it is told to map and nothing else; `error.transport-free` (reviewed)
- Treat the concurrency case as an error type, not as a policy: a write that lost the race raises the context exception with
  `CONCURRENT_MODIFICATION`; how the storage layer detects it and how a transport reports it are not this file's business; `error.concurrency-is-a-type` (reviewed)

## Example — the three throw sites, one type

```java
// 1. a value object: the invariant does not hold
record Quantity(int value) {
    Quantity {
        if (value <= 0) {
            throw new OrderException(OrderException.Error.QUANTITY_NOT_POSITIVE, value);
        }
    }
}

// 2. an aggregate: the command contradicts the state
void assignTo(Ownership ownership) {
    if (ownership == null) {
        throw new OrderException(OrderException.Error.COMMAND_VALUE_MISSING, "ownership");
    }
    if (!Objects.equals(this.ownership, ownership)) {
        this.ownership = ownership;
        events.add(new OwnershipUpdated(orderId, ownership));
    }
}

// 3. an adapter: the outside world failed, the cause is kept
OrderConfiguration read(String name) {
    try {
        return repository.get(name).as(OrderConfiguration.class)
                .orElseThrow(() -> new OrderException(OrderException.Error.DOCUMENT_UNREADABLE, name));
    } catch (RuntimeException unreadable) {
        throw new OrderException(OrderException.Error.DOCUMENT_UNREADABLE, unreadable, name);
    }
}
```

The caller reads `error()` and `details()`; nothing reads `getMessage()` to decide anything.

## What a transport does with it (not here)

A transport maps a small number of `Error` families to its own vocabulary — one handler, one place. The status mapping, the response
body and the "never expose internals" rule belong to the HTTP playbook (`http.rejection-status`, `http.error-handler-single`), and an
integration adapter's retry belongs to the persistence or protocol playbook. This file only guarantees the type and the vocabulary they
map.

## What to avoid / NOGO

- Do not declare one exception class per error (`OrderNotFound`, `OrderAlreadyAssigned`, …): it turns a closed vocabulary into a class
  hierarchy and forces every caller to catch types; `error.single-per-context` (reviewed)
- Do not use a checked exception for a domain refusal — it changes every signature on the way up and buys nothing;
  `error.unchecked-per-context`
- Do not build a message at the throw site (`"quantity " + q + " is not positive"`): the same condition would then read two ways;
  `error.message-in-enum` (reviewed)
- Do not throw `IllegalArgumentException`, `IllegalStateException`, `NullPointerException` or a bare `RuntimeException` from an
  aggregate or a value object; `error.domain-throws-context-type`
- Do not put a status code, a header or a client type into the exception; `error.transport-free` (reviewed)
- Do not swallow a cause: an integration failure keeps the original exception attached; `error.cause-on-integration`
- Do not decide a retry or a fallback inside the exception or the adapter — a failure is reported, not handled;
  `error.adapter-translates-only` (reviewed)
- Do not read a record's accessor in its compact constructor to check an invariant; `vo.invariant-reads-parameters`

## Enforcement

Encoded in `.agents/sqt.yaml` (`sqt check src` → exit `5` on the first `error`):

| Rule | Severity | What it checks |
|---|---|---|
| `error.unchecked-per-context` | error | a type whose name ends `Exception` extends `RuntimeException` |
| `error.type-enum-nested` | error | that type nests an `enum Error` |
| `error.domain-throws-context-type` | error | an aggregate never throws `IllegalArgumentException`, `IllegalStateException`, `RuntimeException` or `UnsupportedOperationException` |

Probed with a violation of each shape; the corpus is green. `error.single-per-context`, `error.message-in-enum`,
`error.details-are-values`, `error.adapter-translates-only`, `error.transport-free` and `error.concurrency-is-a-type` stay review rules —
they describe where a value lives, which no shape check can decide at file grain.
