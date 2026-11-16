# HTTP Adapter

A controller is the only place where an HTTP request becomes a call inside a bounded context, and the only place where an
internal answer becomes a wire payload. It validates, translates, calls exactly one collaborator, and maps the result back.
It never decides business state, never touches a repository, never publishes an event.

Two shapes cover the business API; both are thin and differ only in which collaborator they call and which record they return:

| Shape | Handlers | Injected collaborator | Returns |
|-------|----------|----------------------|---------|
| **Write (command)** | `PATCH`, `PUT`, `POST` | the context's primary port (`*Service`) | the aggregate snapshot record |
| **Read (query)** | `GET` | the read-model component (`*Projection`) | a read model record |

The rule that keeps write and read apart: **the read endpoint must not reach the write model**. A read controller
(`{Area}Controller` over `{Area}Projection`) depends on the projection only, so the read path is physically unable to call a
`*Service` or a `*Repository`.

## How to implement — controller anatomy

- Put the controller in the package of the context it serves and make the class package-private — an adapter is internal to its context; `http.controller-package-private`
- Annotate the class with `@RestController` and constructor-inject exactly one collaborator through `@RequiredArgsConstructor`; `http.single-dependency`
- Do not put `@RequestMapping` on the class — every handler declares its own full path and verb so the route is readable on the method that serves it; `http.no-class-request-mapping`
- Keep handler methods package-private, like the class; `http.handler-package-private`
- Declare `produces` on every handler that returns a body and `consumes` on every handler that takes a `@RequestBody`; `http.explicit-media-type`
- Return the response type directly — no `ResponseEntity`, no manual `HttpStatus` for success; `http.no-response-entity`
- Keep a handler thin; `http.thin-controller` (measured: `function_statements` warning > 5, error > 10; `method_logical_lines` warning > 15, error > 30)
- Map an absent aggregate to `404` — the only status a business adapter raises itself — and never return `Optional` from a handler, because Spring serialises it as `200` with an empty body and the absence stops being visible on the wire; `http.not-found-status` (held at `warning` until its correction is rolled out)
- Let the framework own the two statuses it already produces: a missing required parameter is `400` and an unsatisfiable `Accept` is `406`; writing a handler for either duplicates Spring and drifts from the wire contract; `http.framework-status` (reviewed)

### Command rejection — the complete status mapping

A command can fail in five ways, and each has exactly one status and exactly one owner:

| Failure | Status | Raised by | Body |
|---|---|---|---|
| malformed JSON, a missing required parameter, a violated constraint on the command record | `400 Bad Request` | the framework (`@Valid`), or the one advice for a binding error | the validation problem |
| the aggregate does not exist | `404 Not Found` | the handler (the only status a handler raises itself) | empty |
| the aggregate refuses the command — the state forbids it (already assigned, already packed, closed document) | `409 Conflict` | the one advice, from the domain rejection | the reason, as a value the client can branch on |
| the write lost an optimistic-lock race (`@Version`) | `409 Conflict` — or `412 Precondition Failed` if the API documents preconditions | the one advice | retry hint |
| anything unexpected | `500 Internal Server Error` | the framework, with `include-message: never` | nothing internal |

`406 Not Acceptable` and `415 Unsupported Media Type` stay the framework's: they describe the request envelope, not the command.

- Map every rejection to one of these five and let one `@RestControllerAdvice` translate the domain's rejection type into `409`, so a handler never catches, never branches on an outcome and never builds a status by hand; `http.error-handler-single`
- Return the rejection reason as a small value (an enum or a record) so the client can branch; the message string is not the contract; `http.rejection-status`
- Keep `400` for an unparsable or invalid *request* and `409` for a well-formed command the state refuses — swapping them turns a client bug into a retry loop; `http.rejection-status`
- Use `422` only if the API distinguishes syntactic from semantic rejection **everywhere**; mixing `400` and `422` for the same class of failure is worse than one of them; `http.rejection-status` (reviewed)
- Let the framework's own errors stay framework-raised: an absent required parameter is `400`, an unsatisfiable `Accept` is `406`; re-implementing either drifts from the wire contract; `http.framework-status` (reviewed)
- Let the controller depend only on its own context package and `tools/`; a cross-context need is a contract port injected into the service, never into the controller; `http.no-cross-context` — enforced by the architecture test that binds adapters to their own context, not by `sqt`
- Never inject a `*Repository`, `*DocumentRepository` or any adapter bean — the controller calls the primary port; `http.no-adapter-dependency` (held at `warning` until its correction is rolled out)

## How to implement — API design

- Name the resource in the path, put the identity in a `{placeholder}`, and use no verb segment and no `/api` prefix (`PATCH /{collection}/{id}`, `GET /{collection}`); `http.resource-path` (the `sqt` rule holds the checkable half: a `path` is an absolute literal; "no verb" and "no `/api`" stay review rules)
- Choose the verb by intent: `PATCH` for a partial change of one aggregate, `PUT` for whole-document replacement, `POST` for create, `GET` for a read; `http.verb-matches-intent` (reviewed)
- When two clients need different shapes of the same collection, keep one path and discriminate by media type (`produces = "application/vnd.summary+json"` versus `"application/vnd.detail+json"`) instead of adding a second path or a query flag; `http.media-type-per-view` (reviewed — a rule would have to compare two handlers)
- Return a read model from a read endpoint and the aggregate snapshot from a write endpoint — never a JPA entity, never a `*Editor`, never a persistence document; `http.no-persistence-type-in-api`
- Serve every `GET` from the read model: a `GET` controller holding a `*Service` reaches the write model; `http.no-write-model-in-read`
- Bind collections to `Pageable` and declare both paging parameters as required with `params = {"page", "size"}`; `http.required-paging` (the rule enforces the *declaration* of both parameters — whether the query honours them is review)
- Keep a filter parameter required — a bare handler parameter is already required by Spring, so never declare `required = false` to make an absent filter mean "no filter"; `http.required-filter`

## How to implement — command object

The request body of a mutating business endpoint is a command object: a record that states the intent and holds only the
values the client may change.

- Name the command after the intent (`Update{Concept}`), never `*Dto`, `*Request` or `*Payload`; `http.command-record` (held at `warning`: names are heuristic)
- Declare one component per changeable value object and let `null` mean "not part of this request" — no "replace everything" flag, no partial enum; `http.null-means-absent` (the checkable half: a component is a reference type, never a primitive)
- Put validation annotations on the command's components (`@Valid`), never on the aggregate; `http.validation-on-command` (the rule covers only the aggregate side — that no `@Valid` sits on the aggregate; whether the command components carry one is review)
- Return nothing from the command — the service returns the snapshot the controller serialises; `http.command-no-result`
- Provide a named static factory for the common subset (`Update{Concept}.use(first, second)`), so fixtures and internal callers do not build the full record; `http.command-factory` (held at `warning`)

## Example — write side

A whole write adapter: one dependency, one route, one statement in the handler, the absent case mapped to `404`.

```java
@RestController
@RequiredArgsConstructor
class DeviceController {

    private final DeviceService service;

    @PatchMapping(path = "/devices/{deviceId}",
            consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    DeviceConfiguration patchStation(@PathVariable String deviceId,
                                     @RequestBody @Valid UpdateDevice update) {
        return service.updateDevice(deviceId, update)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
```

The business work happens behind the port and returns the snapshot; the controller adds nothing to it.

```java
@Service
@RequiredArgsConstructor
class DeviceService {

    private final DeviceRepository repository;

    public Optional<DeviceConfiguration> updateDevice(String deviceId, UpdateDevice update) {
        return repository.get(deviceId)
                .map(device -> {
                    update.apply(device);
                    repository.save(device);
                    return device.toDeviceConfiguration();
                });
    }
}
```

The command owns the "which fields were sent" decision, so neither the controller nor the service branches per field.

```java
@Builder
public record UpdateDevice(
        @Valid Location location,
        @Valid OpeningHours openingHours,
        @Valid Settings settings,
        @Valid Ownership ownership) {

    public static UpdateDevice use(Ownership ownership, Location location) {
        return builder()
                .location(location)
                .ownership(ownership)
                .build();
    }

    public void apply(DeviceConfigurationEditor device) {
        if (location != null) {
            device.updateLocation(location);
        }
        if (openingHours != null) {
            device.updateOpeningHours(openingHours);
        }
        if (settings != null) {
            device.updateSettings(settings);
        }
        if (ownership != null) {
            device.assignTo(ownership);
        }
    }
}
```

## Example — read side

One path, two representations selected by media type, mandatory paging, and no access to the write model.

```java
@RestController
@RequiredArgsConstructor
class DeviceReadsController {

    private final ReadModelsProjection reads;

    @GetMapping(path = "/devices", params = {"page", "size"},
            produces = "application/vnd.summary+json")
    Page<DeviceSummary> getSummary(String provider, Pageable pageable) {
        return reads.findAllSummary(provider, pageable);
    }

    @GetMapping(path = "/devices", params = {"page", "size"},
            produces = "application/vnd.pin+json")
    Page<DevicePin> getPins(String provider, Pageable pageable) {
        return reads.findAllPins(provider, pageable);
    }

    @GetMapping(path = "/devices/{deviceId}",
            produces = APPLICATION_JSON_VALUE)
    DeviceDetails getDetails(@PathVariable String deviceId) {
        return reads.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
```

Every read method on the projection is explicitly read-only and returns already-shaped records — the controller performs no
mapping. The entity behind these queries stores each read model as its own JSONB column.

```java
@Component
@Transactional
@AllArgsConstructor
class ReadModelsProjection {

    private final DeviceReadsRepository repository;

    @Transactional(readOnly = true)
    public Page<DeviceSummary> findAllSummary(String provider, Pageable pageable) {
        return repository.findAllByProvider(provider, pageable)
                .map(DeviceReadsEntity::getSummary);
    }

    @Transactional(readOnly = true)
    public Page<DevicePin> findAllPins(String provider, Pageable pageable) {
        return repository.findAllByProvider(provider, pageable)
                .map(DeviceReadsEntity::getPin);
    }

    @Transactional(readOnly = true)
    public Optional<DeviceDetails> findById(String deviceId) {
        return repository.findById(deviceId)
                .map(DeviceReadsEntity::getDetails);
    }
}
```

The read model record is where the composition happens: it takes the snapshot plus the last known foreign value and decides
what the client sees.

```java
record DeviceSummary(
        String deviceId,
        Location location,
        List<String> statuses) {

    static DeviceSummary ofNullable(DeviceConfiguration details, DeviceStatuses statuses) {
        if (details == null || details.location() == null) {
            return null;
        }
        return new DeviceSummary(
                details.deviceId(),
                details.location(),
                Optional.ofNullable(statuses)
                        .map(DeviceStatuses::statuses)
                        .orElse(List.of("Faulted")));
    }
}
```

The second representation is a different record over the same row, so the two views never share a shape.

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

When a representation needs two sources, the record composes them and `@JsonUnwrapped` keeps the wire shape flat — the client
never learns that two contexts produced the answer.

```java
record DeviceDetails(
        @JsonUnwrapped
        DeviceConfiguration configuration,
        BootNotification boot) {
}
```

## What to avoid / NOGO

- Do not inject a `*Repository` or a `*DocumentRepository` into a controller; call the primary port instead; `http.no-adapter-dependency`
- Do not return `Optional` from a handler — it produces `200` with an empty body, not `404`; `http.not-found-status`
- Do not put `@RequestMapping` on the class and let handlers inherit verbs and paths; `http.no-class-request-mapping`
- Do not use field injection or several collaborators in one controller; a second dependency means the handler is doing work that belongs in a service; `http.single-dependency`
- Do not return `ResponseEntity` or build status codes by hand when the returned record is the answer; `http.no-response-entity`
- Do not catch a domain rejection inside a handler, and do not answer a refused command with `400` or `500`; one advice owns the mapping and a refusal is `409`; `http.error-handler-single`
- Do not serialise a JPA entity, a persistence document or the aggregate editor; they are not API contracts and change with the storage strategy; `http.no-persistence-type-in-api`
- Do not serve a `GET` from the write model because the projection looks stale; fix the projection; `http.no-write-model-in-read`
- Do not add a second path or a `?view=` flag for another shape of the same collection; use a media type; `http.media-type-per-view`
- Do not put a verb in the path (`/{collection}/update`) — the verb is in the HTTP method; `http.resource-path`
- Do not branch on business state in a controller — mapping a domain outcome enum to a wire enum is adapter duty, deciding the outcome is not; `http.thin-controller`

## Enforcement

All 18 expressible `http.*` rules are encoded in the `sqt` config and evaluated by `sqt check src`; `http.thin-controller` is
the one metric. Probe every rule with a synthetic violation before trusting the gate.

| Status | Rules |
|--------|-------|
| `error` (gates) | `http.controller-package-private`, `http.single-dependency`, `http.no-class-request-mapping`, `http.handler-package-private`, `http.explicit-media-type`, `http.no-response-entity`, `http.resource-path`, `http.no-persistence-type-in-api`, `http.no-write-model-in-read`, `http.required-paging`, `http.required-filter`, `http.null-means-absent`, `http.validation-on-command`, `http.command-no-result` |
| `warning` (reported, never gates) | `http.not-found-status`, `http.no-adapter-dependency`, `http.command-record`, `http.command-factory` |
| measured | `http.thin-controller` — `function_statements` warning > 5, error > 10; `method_logical_lines` warning > 15, error > 30 |
| reviewed (no checkable form) | `http.verb-matches-intent`, `http.media-type-per-view`, `http.framework-status`, `http.rejection-status`, `http.error-handler-single` |
| enforced elsewhere | `http.no-cross-context` — the architecture test that binds adapters to their own context |

A rule stays at `warning` while its correction is still being rolled out across a codebase; promote it to `error` once the
gate is clean.
