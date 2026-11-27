## 14 Gaps

Four things a client needs that this repository cannot state. Each names what would close it.

**GAP-1 — The contract has no machine-readable form.** There is no OpenAPI document, no JSON schema and no
client artifact: the field tables in chapter 11 were read from the records the handlers answer with, and the
statuses in chapter 3 from the one advice that maps them. Consequences: a generated client is not possible,
and a change to a record is not caught by any check on the contract. Closing it means publishing an OpenAPI
document from the build, which would also make the operation chapters generated instead of partly authored.

<!-- evidence: services/shopping/build.gradle:24-34, services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:41-48 -->

**GAP-2 — No operation has a recorded request or response transcript.** The executable scenario this service
carries addresses the management surface; the contract of record is a `prove` document that the
documentation pipeline does not read. A client therefore sees no example body, and a response example is an
omission rather than a decision. Closing it means teaching the pipeline the scenario format, or adding a
recorded scenario in the format it already reads.

<!-- evidence: services/shopping/e2e/healthcheck.hurl:1-2, services/shopping/build.gradle:76-79 -->

**GAP-3 — Nothing states versioning, deprecation, pagination or rate limits.** The routes carry no version
segment, no deprecation policy exists, `GET /baskets` returns one envelope with no `page`/`size`, and no rate
limit is declared in any manifest or filter chain. A client that needs any of these has no answer here, and
none was invented: the specification's scope excludes them and the deployment states none.

<!-- evidence: docs/specs/basket/index.md:150-157, services/shopping/src/main/java/shop/shopping/basket/BasketsController.java:29-32 -->

**GAP-4 — The offer read is a fixture, so its failure vocabulary is an assumption.** The `503` status and the
`stale` flag exist because a read can fail. What a real provider's failure looks like — a timeout, a partial
answer, a stock count that is not a count — is written into element 08 from golden assumptions that the
browsing slice has not yet confirmed. A client that branches on `503` is branching on this service's promise,
which is correct; a client that expects the provider's own codes is not.

<!-- evidence: docs/specs/basket/log.md:102, services/shopping/docs/offer-fixture.md:99-101 -->
