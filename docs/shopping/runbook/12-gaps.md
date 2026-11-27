## 12 Gaps

What an operator needs and the repository does not state. Each entry names what would close it.

**GAP-1 — No alert rule, threshold or receiver.** Nothing in the repository says when a human should be
woken, so every failure in chapter 6 is found by a client's report or by a probe. Closing it needs at least
one rule on a signal the service already exports: a readiness flap, a restart count, a `5xx` rate or a `409`
rate per basket.

<!-- evidence: services/shopping/src/main/resources/application.yml:47-50, services/shopping/k8s/base/deployment.yaml:121-144 -->

**GAP-2 — No dashboard.** No dashboard definition of any kind is checked in, so nothing prepares a view of
the metrics, traces or logs the service produces. The collectors are there; the view is not.

<!-- evidence: services/shopping/k8s/infra/otel-collector.yaml:1-31 -->

**GAP-3 — No backup, restore or retention policy.** Chapter 9 states the order in which dependencies must
come up and says explicitly that recoverability is the database's. Nothing in this repository states what
that is.

<!-- evidence: services/shopping/k8s/kustomization.yaml:1-5, services/shopping/src/main/resources/application.yml:3-13 -->

**GAP-4 — No owner and no escalation path.** Chapter 10 records it: no team, no rotation, no contact.

<!-- evidence: services/shopping/AGENTS.md:1-11 -->

**GAP-5 — No service-level objective.** No availability, latency or error-budget target is stated, so there
is no agreed answer to "is this incident an incident". The business guide records the same absence against
criticality, which is the number that would give this one a target.

<!-- evidence: docs/specs/basket/index.md:173 -->

**GAP-6 — The management surface is wider than an operator needs.** `/actuator/env` and `/actuator/prometheus`
are readable by any caller who holds a valid token of the realm, including a customer token, because the
filter chain's `/actuator/**` rule asks for authentication and not for a service authority. No secret value
is exposed — the Secret arrives as environment variables the endpoint reports by name — but the resolved
configuration is. Recording it here is the honest form; narrowing the rule is a change to the service, not to
this manual.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:64-67, services/shopping/src/main/resources/application.yml:47-50 -->

**GAP-7 — A probe path and a scenario disagree.** The filter chain permits `/actuator/info` to everyone while
the service's own healthcheck scenario asserts `401` for an anonymous call to it. One of the two is stale:
either the rule widened after the scenario was written, or the scenario was never re-run after the rule
changed. Nothing in the repository resolves which, and the deployment does not use that endpoint for a probe,
so no operational behaviour depends on it — but a reader who trusts the scenario will misread the access
matrix, and the integrator guide's operational chapter inherits the contradiction from this repository rather
than resolving it.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:64-66, services/shopping/e2e/healthcheck.hurl:10-12 -->
