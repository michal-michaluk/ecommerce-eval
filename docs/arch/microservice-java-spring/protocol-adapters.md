# Protocol Adapters

A protocol adapter translates one wire format into the context's own record and back. It never decides anything: the same `*Service`
method answers every protocol version, and the only thing a version package owns is its own vocabulary.

```
<root-package>/{context}/
    {Context}Service                     # the use case, protocol-agnostic
    {CanonicalRecord}                    # the canonical record every adapter produces
    protocols/
        {version-a}/
            {Version}Controller          # @RestController, one route
            {CanonicalRecord}Request     # the wire payload + its mapping
            {CanonicalRecord}Response    # the wire answer
        {version-b}/
            …                            # the same three types
```

Sub-packages are the **only** sub-packages a context may have, one per protocol version (`structure.protocol-subpackage`).

## Three types per version, nothing more

| Type | Role | Access |
|---|---|---|
| `{Version}Controller` | route, translate in, translate out | package-private `@RestController` |
| `{CanonicalRecord}Request` | the wire payload as a record, plus `to{CanonicalRecord}(identity)` | package-private record |
| `{CanonicalRecord}Response` | the wire answer as a record, plus the version's own `Status` enum | package-private record |

- Create one package per protocol version, named after the version as the wire names it (`{version-a}`, `{version-b}`), never `v1`/`v2`; `protocol.version-package`
- Keep all three types package-private — a protocol record is the least reusable type in a context; the canonical record is what
  crosses; `protocol.package-private-types`
- Name the controller after the version (`{Version}Controller`) and keep the other two types named exactly
  `{CanonicalRecord}Request` / `{CanonicalRecord}Response` in each package, so the two versions read side by side; `protocol.same-shape-per-version`
- Give the version its own `Status`/`Reason` enums even when two versions agree — one version's enum never constrains another's; `protocol.enum-per-version`
- Keep the numbers of the wire where they belong: a field the canonical record does not need stays in the request record and is dropped
  in the mapping; `protocol.drop-unknown-wire-fields`

## The request owns the mapping

The payload is a record; the translation to the canonical record is a method on it, so the controller stays one expression. The canonical,
request and response records are all shown, so the example stands alone:

```java
record BootNotification(String deviceId, Protocols protocol, String vendor, String model,
                        String serialNumber, String firmwareVersion) {

    enum Protocols { IoT16, IoT20 }
}

@Builder
record BootNotificationResponse(String currentTime, int interval, Status status) {

    enum Status { Accepted, Pending, Rejected }
}

record BootResponse(Instant serverTime, int intervalInSeconds, State state) {

    enum State { UNKNOWN, EXISTING }

    <T> T state(Function<State, T> read) {
        return read.apply(state);
    }
}

record BootNotificationRequest(
        Device device,
        Reason reason) {

    record Device(
            String serialNumber,
            String model,
            Modem modem,
            String vendorName,
            String firmwareVersion) {
    }

    record Modem(String iccid, String imsi) {
    }

    enum Reason {
        ApplicationReset, FirmwareUpdate, LocalReset, PowerUp, RemoteReset,
        ScheduledReset, Triggered, Unknown, Watchdog
    }

    BootNotification toBootNotification(String deviceId) {
        return new BootNotification(
                deviceId, BootNotification.Protocols.IoT20,
                device.vendorName, device.model,
                device.serialNumber, device.firmwareVersion
        );
    }
}
```

- Put `to{CanonicalRecord}(...)` on the request record, not in the controller — the wire→domain mapping is a property of the wire shape; `protocol.request-owns-mapping`
- Pass the path variable in as an argument (`to{CanonicalRecord}(identity)`); the payload never carries the identity it was routed
  with; `protocol.identity-from-path`
- Map to the **canonical** record only, and return it from the method; a protocol record must never be a parameter or a return type of a
  context service; `protocol.canonical-record`
- Keep the version marker inside the canonical record (the `Protocols` enum constant), so a consumer can tell the versions apart without
  reading a header; `protocol.version-in-payload`

## The controller is one expression

```java
@RestController
@RequiredArgsConstructor
class IoT16Controller {

    private final CommunicationService service;

    @PostMapping(path = "/protocols/iot16/bootnotification/{deviceId}",
            consumes = "application/json", produces = "application/json")
    BootNotificationResponse handleBootNotification(@PathVariable String deviceId,
                                                    @RequestBody BootNotificationRequest request) {
        return service.handleBoot(request.toBootNotification(deviceId))
                .map(resp -> BootNotificationResponse.builder()
                        .currentTime(resp.serverTime().toString())
                        .interval(resp.intervalInSeconds())
                        .status(resp.state(state -> switch (state) {
                                    case UNKNOWN -> Rejected;
                                    case EXISTING -> Accepted;
                                })
                        ).build()
                );
    }
}
```

- Keep the route absolute and versioned: `/protocols/{version}/{message}/{identity}`, no class-level `@RequestMapping`
  (`http.no-class-request-mapping`); `protocol.path-versioned`
- Inject the one `*Service`, never a repository, a projection or another protocol adapter; `http.single-dependency`
- Map the answer inside the controller — the canonical answer exposes functional accessors (`map`, `state`, `intervalInSeconds`) so each
  version maps without touching fields; `protocol.response-mapped-in-controller`
- Build the response record with `@Builder` and keep `currentTime` a `String` the wire can read (`Instant.toString()`); `protocol.response-builder`
- Declare `produces`/`consumes` explicitly on every version's route; `http.explicit-media-type`
- Keep the version's answer to the wire's own vocabulary — a status the version cannot express is simply not produced; `protocol.status-per-version`
- Watch a dead status value: when every version collapses the same wire states into the same statuses, a status the enum declares is
  never produced; either the service grows the state or the enum shrinks; `protocol.no-dead-status` (reviewed)

## Adding a protocol version

1. Create `protocols/{version}/` with the three types; `protocol.version-package`
2. Copy the request/response record shape from the closest version and adapt the fields; keep the record names identical; `protocol.same-shape-per-version`
3. Add the constant to the canonical record's version enum — that enum is the registry of supported versions, and the mapping method needs
   it; `protocol.extend-canonical-enum`
4. Set the route to `/protocols/{version}/{message}/{identity}` and map the answer; `protocol.path-versioned`
5. Add `{Protocol}{Version}MessagesFixture` with one realistic wire payload per message, in the version package under the test tree; `protocol.fixture-per-version`
6. Change nothing in the context service — if the new version needs a new answer field, the canonical answer record changes for all
   versions, which is a decision, not a translation; `protocol.service-unchanged`
7. Add the context's architecture test at the same time; `archunit.context-test-location`

Steps 3 and 6 are the two places where a new version is *not* local to its package: the enum in the canonical record and the answer
record in the service. Both are deliberate — the canonical record is the boundary, and a version must not invent a parallel one.

## Tests

One fixture per version, holding the payload as the peer sends it:

```java
public class Ocpp16MessagesFixture {

    public BootNotificationRequest bootNotification() {
        return new BootNotificationRequest(
                new BootNotificationRequest.Device(
                        "820394A93203", "CPF25 Family",
                        new BootNotificationRequest.Modem("112233445566778899C1", "082931213347973812"),
                        "Garo", "1.1"),
                BootNotificationRequest.Reason.PowerUp);
    }
}
```

- Name the fixture after the protocol family and version (`{Protocol}{Version}MessagesFixture`), one method per message; `protocol.fixture-per-version`
- Assert the translation, not the routing: the fixture's payload mapped through `to{CanonicalRecord}(identity)` equals the expected
  canonical record — that is the whole adapter's contract; `protocol.test-translation`
- Add one HTTP-level test per version only for the status mapping — one case per wire state, asserted through the route; `protocol.test-status-mapping`
- Keep a changed request field breaking a test, not a runtime call: a version without a fixture and a translation test is an untested
  adapter; `protocol.adapter-tested`

## What to avoid / NOGO

- Do not put a protocol record into a service signature, a policy or a published event — translate first; `protocol.canonical-record`
- Do not put two versions in one package with a version flag (`if (v2)`) — the package is the version; `protocol.version-package`
- Do not share one request record between versions "because they look alike": they diverge on the first firmware release; `protocol.same-shape-per-version`
- Do not decide business state in the controller — the status mapping in the controller is a translation of the service's answer, not a
  rule; `mediator.no-logic` for the same reason
- Do not accept an unknown identity silently: an unknown wire state maps to a rejection; changing that is a business decision made in the
  service, not in a controller; `protocol.service-unchanged`
- Do not add a protocol-specific column, table or repository — the wire format stops at the controller; `protocol.no-wire-in-storage` (reviewed)
- Do not version the canonical record per protocol (`{CanonicalRecord}V2`) — versions belong in the canonical enum; `protocol.extend-canonical-enum`

## Enforcement

`protocol.*` shape checks expressible as `sqt` checks: `protocol.version-package` (a `protocols/<version>/` folder holds exactly one
`*Controller`), `protocol.package-private-types` (nothing `public` under `protocols/`), `protocol.canonical-record` (no `protocols` type
appears in a `*Service` signature), `protocol.path-versioned` (the mapping path starts with `/protocols/`). The translation and
status-mapping rules are tests (`protocol.test-translation`, `protocol.test-status-mapping`), not checks.
