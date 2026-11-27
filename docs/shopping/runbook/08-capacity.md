## 8 Capacity and known limits

The numbers below are the only ones the repository states. Nothing here is a measurement: no load was
applied, and no material carries a volume, latency or concurrency figure.

| Resource | Value | Where it comes from |
|---|---|---|
| Replicas | 1 | the Deployment |
| CPU request / limit | 250m / 1 | the base container |
| Memory request / limit | 768Mi / 1.5Gi | the base container |
| JVM heap policy | `-XX:MaxRAMPercentage=75`, `-XX:+UseZGC` | the image |
| Database connections | pool of 15 | the application configuration |
| Readiness / liveness period | 5 s / 10 s | the probes |
| Cold-start budget | 60 attempts × 10 s on the startup probe | the startup probe |

: The size of the thing, as declared.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:7-8, services/shopping/k8s/base/deployment.yaml:124-151, services/shopping/build.gradle:236-240, services/shopping/src/main/resources/application.yml:3-6 -->

### 8.1 What these numbers imply

- **The connection pool is the concurrency ceiling for writes.** Fifteen connections, one document per
  basket, and an optimistic lock on each row: a burst of writes to one basket serialises, and a burst across
  baskets is bounded by the pool.
- **The heap is bounded by the limit, not by the request.** At a 1.5Gi limit the JVM may take roughly 1.1Gi
  of heap. A container that exceeds the limit is killed by the kernel, and that kill looks exactly like a
  liveness failure to an operator.
- **One replica has a second cost.** The fixture's stock answers are per process, so the deployment cannot be
  scaled out without moving the stock transition — this is a property of the stand-in, not of the basket, but
  it is the state of the deployment today.

<!-- evidence: services/shopping/src/main/resources/application.yml:5-6, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:130-140, services/shopping/docs/offer-fixture.md:100-110 -->

### 8.2 The limits that are rules rather than resources

Two business limits exist and neither is configurable: an offer appears once per basket and its quantity is
an `int` with no stated maximum, and a basket has no item-count limit in this increment. The specification
records the absence of a limit as a decision, and records that a later increment will respect the
warehouse's stock bands.

<!-- evidence: docs/specs/basket/log.md:103, docs/specs/basket/log.md:98 -->
