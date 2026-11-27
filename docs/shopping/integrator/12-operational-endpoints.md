## 12 Operational endpoints

The framework publishes a management surface. It is not part of the basket contract, and this chapter
exists because a client or an operator will meet it: three of its endpoints are reachable, and one of them
is reachable by any caller who holds a token of the realm.

| Endpoint | Path | Who may read it | What it answers |
|---|---|---|---|
| health | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | anyone | the aggregate status, and the two probe groups the Deployment uses |
| info | `/actuator/info` | anyone | the info contributors the build declares — none are declared |
| env | `/actuator/env` | any authenticated caller | the resolved environment properties |
| prometheus | `/actuator/prometheus` | any authenticated caller | the Prometheus scrape format of the process's meters |

<!-- evidence: services/shopping/src/main/resources/application.yml:47-50, services/shopping/src/main/java/shop/shopping/AppConfiguration.java:64-67 -->

Two of those rows deserve a client's attention. `/actuator/env` and `/actuator/prometheus` fall through to
the authenticated-request rule, so a customer token is enough to read the resolved environment — including
the datasource URL and every `basket.*` value. Nothing in that response is a secret value, because the Secret
arrives as environment variables the endpoint reports only by name, but the configuration surface is wider
than a basket client needs.

`/actuator/info` is the row where this repository contradicts itself: the filter chain permits it to
everyone, while the service's own healthcheck scenario still asserts `401` for an anonymous call to it. One
of the two is stale, nothing in the repository says which, and no probe depends on the endpoint — so it is
recorded as a gap rather than resolved by a document.

<!-- evidence: services/shopping/e2e/healthcheck.hurl:10-12, services/shopping/src/main/java/shop/shopping/AppConfiguration.java:64-67, services/shopping/k8s/base/deployment.yaml:106-120 -->

### 12.1 What a client should use instead

Nothing. The management surface is an operator's, it has no versioned contract, no compatibility promise
and no error body of its own, and a client that reads it is coupled to a framework's decision rather than to
this service's. The one endpoint a probe may use is the health group, and it exists for Kubernetes, not for
a client.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:121-144 -->
