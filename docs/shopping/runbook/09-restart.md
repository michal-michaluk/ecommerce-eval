## 9 Restart and recovery

What survives, what is re-derived, and what nobody has written down.

### 9.1 What survives, and it is the important half

The aggregate is stored as one JSONB document per basket, and the write happens before the events are
published. A restarted process therefore reads exactly what the customer last saw: the rows, their
quantities, their selection flags, their prices and the four totals. The specification's own acceptance run
restarted the pod mid-scenario and compared both sides; the content was identical.

<!-- evidence: services/shopping/src/main/resources/db/changelog/0001-basket.yaml:3-39, docs/specs/basket/prove-of-done.md:66-66 -->

### 9.2 What is re-derived, and why that is visible to a customer

One thing does not survive: the fixture's per-offer read counters. They live in the process, so after a
restart a row that had gone out of stock can be answered as available again until the catalogue is asked
enough times. The specification records this as the demonstrated limit of the restart row rather than as a
defect: content is durable, a *derived* availability is not.

<!-- evidence: services/shopping/docs/offer-fixture.md:100-110, docs/specs/basket/prove-of-done.md:131-133 -->

### 9.3 Recovery order

The service has two hard dependencies at start and one soft one. Start them in this order:

1. **The database.** Liquibase runs at start, and a pod that cannot reach it does not become ready.
2. **The identity provider.** The issuer's keys are fetched lazily, on the first token; a pod starts without
   it and answers `401` until it is reachable.
3. **The collector**, if it is deployed at all — the application exports to it and does not need it to serve
   traffic. A missing collector loses telemetry, not requests.

There is nothing to replay and nothing to rebuild: the events the aggregate publishes are in-process and have
no consumer in this increment, so a restart neither loses a queued message nor needs one re-delivered.

<!-- evidence: services/shopping/src/main/resources/application.yml:7-19, services/shopping/k8s/overlays/k3d/deployment-patch.yaml:31-53, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:40-70 -->

### 9.4 What is not written down

**GAP-3 — No backup, restore or disaster-recovery procedure.** No backup manifest, job, retention policy or
restore runbook exists in this repository. The basket table's recoverability is the database's, and this
repository does not say what that is. Closing it needs the database's own backup policy stated, and a
restore that was actually run once.

<!-- evidence: docs/specs/basket/index.md:8, services/shopping/k8s/base/configmap.yaml:1-28 -->
