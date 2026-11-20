## 12 Operating notes

<!-- evidence: services/fulfillment/src/main/resources/application.yml:55 -->
<!-- evidence: services/fulfillment/src/main/resources/application.yml:41 -->
<!-- evidence: services/fulfillment/src/main/resources/application.yml:50 -->
<!-- evidence: services/fulfillment/k8s/base/deployment.yaml:8 -->

**Management surface.** `health`, `info`, `env` and `prometheus` are exposed
(`application.yml:55`). `health` and `info` are public; every other actuator endpoint answers any
authenticated caller, which is wider than the customer scope's contract and is recorded as a risk in
`spec.md` §Open Topics (`operator-input#8`). `env` is enabled on purpose (`:55`) and exposes the resolved
configuration, including the datasource URL; Spring's sanitiser hides values whose key looks like a
credential.

**Pollers.** Two scheduled readers touch the outside world and both are configured by property, not by
code:

| Poller | Property | Default | Effect when it fails |
| --- | --- | --- | --- |
| carrier delivery legs | `fulfillment.carrier.poll-interval` (`:41`) | 60 s | the leg is not recorded; the next poll re-reads it because application is idempotent on `(waybill, leg)` |
| warehouse stock changes | `fulfillment.warehouse.poll-interval` (`:50`) | 60 s | no `StockChanged` is raised; the cursor does not advance, so the window is read again |

Both pollers hold a database connection for the duration of the remote call, on a pool of 15
(`application.yml:6`). The cursor has no advisory lock, so two replicas would race the watermark. The
deployment pins `replicas: 1` (`k8s/base/deployment.yaml:8`), which is the only thing that makes the
cursor correct; the constraint is recorded, not enforced by code.

**Unconfigured integrations.** The warehouse and the carrier are each optional at boot: without a token
the adapter bean is absent, the service starts, and the first command that needs it is refused with
`WAREHOUSE_NOT_WIRED` or `CARRIER_NOT_WIRED` rather than silently doing nothing. A scan registers the
parcel, so a deployment with no carrier cannot pick at all — the pick workstation is unusable although
nothing about picking needs the carrier. That coupling is deliberate (`D11`) and its operational
consequence is a gap below.

**Shutdown.** `server.shutdown: graceful` (`application.yml:24`): in-flight requests finish, and a poller
that is mid-call is not interrupted.
