## 12 Operating notes

<!-- evidence: services/fulfillment/src/main/resources/application.yml:55 -->
<!-- evidence: services/fulfillment/src/main/resources/application.yml:41 -->
<!-- evidence: services/fulfillment/src/main/resources/application.yml:50 -->

**Management surface.** §11 tabulates the four exposed endpoints and the two public ones. What it does not
state is why `env` is enabled: deliberately (`application.yml:55`), and the endpoint exposes the resolved
configuration, including the datasource URL, while Spring's sanitiser hides values whose key looks like a
credential. That the actuator prefix answers any authenticated caller — wider than the customer scope's
contract — is `GAP-1` and an accepted risk in `spec.md` §Open Topics (`operator-input#8`).

**Pollers.** Two scheduled readers touch the outside world and both are configured by property, not by
code:

| Poller | Property | Default | Effect when it fails |
| --- | --- | --- | --- |
| carrier delivery legs | `fulfillment.carrier.poll-interval` (`:41`) | 60 s | the leg is not recorded; the next poll re-reads it because application is idempotent on `(carrier identity, leg)` |
| warehouse stock changes | `fulfillment.warehouse.poll-interval` (`:50`) | 60 s | no `StockChanged` is raised; the cursor does not advance, so the window is read again |

Both pollers hold a database connection for the duration of the remote call, on a pool of 15
(`application.yml:6`). The cursor has no advisory lock, so two replicas would race the watermark; the single
replica §10 records is the only thing that makes the cursor correct, and the constraint is recorded, not
enforced by code.

**Unconfigured integrations.** The warehouse and the carrier are each optional at boot: without a token
the adapter bean is absent, the service starts, and the first command that needs it is refused with
`WAREHOUSE_NOT_WIRED` or `CARRIER_NOT_WIRED` rather than silently doing nothing. A scan registers the
parcel, so a deployment with no carrier cannot pick at all — the pick workstation is unusable although
nothing about picking needs the carrier. That coupling is deliberate (`D11`) and its operational
consequence is `GAP-12`.

**Shutdown.** `server.shutdown: graceful` (`application.yml:24`): in-flight requests finish, and a poller
that is mid-call is not interrupted.
