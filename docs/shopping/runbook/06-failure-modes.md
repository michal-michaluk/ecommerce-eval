## 6 Failure modes

One failure at a time: what an operator sees, what it means, and what to do. There is no alert for any of
these — the signal column is what a person can see, not what a page says.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:121-144 -->

### 6.1 The readiness probe fails and traffic stops arriving

**Signal** — the pod leaves the Service's endpoints; `kubectl get pods` shows `READY 0/1` while the container
stays up.
**Meaning** — the container answers, but its readiness group reports down. The group is the framework's own:
a database that cannot be reached and a `health` indicator that has failed both take it down.
**What to do** — check the logs for the datasource, check the Secret's three values and the database's
reachability from the pod. A restart does not fix a wrong URL and does fix a broken connection pool.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:138-144, services/shopping/src/main/resources/application.yml:3-6 -->

### 6.2 The liveness probe fails and the container restarts

**Signal** — restarts with no readiness flap first; `kubectl describe` shows the container killed by the
liveness probe.
**Meaning** — six consecutive failures at a ten-second period on `/actuator/health/liveness`. The startup
probe exists so that a cold start is not one of those failures: it allows sixty attempts before the liveness
probe takes over.
**What to do** — read the log of the previous container. A memory limit is a suspect: the container asks for
768Mi, caps at 1.5Gi, and the JVM is told to use 75 % of the limit.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:124-137, services/shopping/build.gradle:236-240 -->

### 6.3 Requests answer `409`

**Signal** — a client reports `409` with `{"error":"CONCURRENT_MODIFICATION"}`.
**Meaning** — two writes raced on one basket document, and one lost the optimistic lock. Because every
answering route re-reads the offers and stores when anything moved, this can happen on a read as well as on
a command.
**What to do** — tell the client to retry; that is the contract. If it is frequent for one customer, look for
a client retry loop with two tabs open — the service writes on every render of a changed basket, and two
concurrent renders of the same basket are the common cause.

<!-- evidence: services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:143-146, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:262-298, docs/specs/basket/log.md:132 -->

### 6.4 Requests answer `503 OFFER_READ_UNAVAILABLE`

**Signal** — an add or a quantity command answers `503`.
**Meaning** — the offer read failed and there is no snapshot to describe the row with: the command that would
have stored it is refused. A render of a basket that already holds the row does not answer `503`; it greys the
row and reports `unavailable` with `stale: true`.
**What to do** — nothing in this service. The provider (today: the fixture) is the failing dependency; the
row is blocked from handover until a read succeeds, which is the recorded decision.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:310-325, docs/specs/basket/log.md:113 -->

### 6.5 Every request answers `401`

**Signal** — all routes, including the basket ones, answer `401` with an empty body.
**Meaning** — no token validated. Either the issuer address is wrong for this environment, or the identity
provider is down and the issuer's keys cannot be fetched.
**What to do** — check `JWT_ISSUER_URI` in the pod's ConfigMap and the realm's reachability. The packaged
default names `keycloak.invalid`, a domain reserved by RFC 2606, precisely so this failure is a start-up
failure in a deployment that states nothing rather than a silent trust in a stranger.

<!-- evidence: services/shopping/src/main/resources/application.yml:14-19, services/shopping/src/main/java/shop/shopping/AppConfiguration.java:76-82 -->

### 6.6 Every basket request answers `403`, but `/actuator/env` works

**Signal** — `403` on every `/baskets` route, with an empty body, from a client whose token works elsewhere.
**Meaning** — the token is valid but carries no `customer` scope. A realm-`shopping` service token is exactly
that shape, and so is a customer token minted with the wrong client scope.
**What to do** — read the token's `scope` claim. The service demands `SCOPE_customer` and has no second
authority; there is no role to grant.

<!-- evidence: services/shopping/src/main/java/shop/shopping/Scopes.java:10, services/shopping/src/main/java/shop/shopping/AppConfiguration.java:68-73 -->

### 6.7 An add answers `404 OFFER_NOT_FOUND` for an offer that exists

**Signal** — the catalogue lists the offer; the basket says it does not exist.
**Meaning** — the deployed catalogue is the fixture, and the fixture knows only the offers its seed names. An
offer nobody seeded is answered as removed without a description, which is the `404` the contract expects for
an unknown id.
**What to do** — add the offer to the seed and redeploy; do not read the `404` as a code defect.

<!-- evidence: services/shopping/docs/offer-fixture.md:44-60, services/shopping/k8s/base/configmap.yaml:45-58 -->

### 6.8 What has no signal at all

The repository declares no alert, no dashboard and no threshold, so the seven situations above are found by a
client's report, a probe's state or a human reading a log. Chapter 12 records that as the manual's biggest
gap rather than as a procedure.

<!-- evidence: services/shopping/src/main/resources/application.yml:47-50, services/shopping/k8s/base/deployment.yaml:121-144 -->
