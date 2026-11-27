## 7 Resilience findings

What the deployment does not define. Each row is a finding, not advice: the repository is silent, and silence
here is what an operator has to plan around.

| Capability | State in this repository | Consequence |
|---|---|---|
| Alerting | absent — no rule, no threshold, no receiver | nothing wakes a human; see chapter 12, GAP-1 |
| Dashboard | absent — no dashboard definition of any kind | no view of the telemetry exists unless someone builds one |
| Service-level objective | absent | no agreed measure of whether the service is doing its job |
| Replicas / high availability | one replica, `replicas: 1` | a restart is a full outage of this service's routes |
| Pod disruption budget | absent | a node drain takes the pod down without a floor |
| Autoscaler | absent | capacity is the resource limits, nothing more |
| Retries inside the service | absent in the code that reads offers; the retry library is on the classpath and `@EnableRetry` is declared | a failed offer read is a failed row, not a retried one |
| Circuit breaker | absent | a provider outage is absorbed as per-row failures, not as a fast refusal |
| Timeouts on the offer read | none declared | the read is in-process today; a real provider call would inherit the framework's defaults |
| Backup and restore | absent — no backup manifest, no job, no retention policy | the basket table's recovery depends on the database's own operations, which this repository does not describe |
| Vertical scaling statement | absent | the JVM is told to use 75 % of a 1.5Gi limit; nothing states what load it is sized for |

: Capabilities an operator would look for, and their state in the repository.

Two of these deserve expansion because they are easy to misread as working features.

**`@EnableRetry` is declared and nothing is retried.** The annotation is on the application configuration and
the retry library is a dependency, but no method carries a retry annotation and no read is retried. The
declaration is the scaffold's, and a reader who sees it may believe a failed offer read is retried; it is
not.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:25-29, services/shopping/build.gradle:31-31 -->

**A healthy read is not a fresh read.** The service stores when a read moved anything, and a read that
answered exactly what the row already said moves only the read instant. That is a deliberate write of
freshness, and it is why a `409` can reach a `GET`. Any resilience plan that assumes reads never write is
wrong for this service.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:180-197, docs/specs/basket/log.md:129 -->
