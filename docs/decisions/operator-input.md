# Operator input — recorded answers

One row per question the source could not answer. Answers are verbatim-in-substance and dated; authored
segments cite the id as `<!-- evidence: operator-input#N -->`. Nothing here is inferred from code.

| id | Date | Question | Answer |
| --- | --- | --- | --- |
| 1 | 2026-11-19 | Which documents does the docset contain? | System documentation, integrator guide, business guide. No operations manual — deployment and telemetry chapters live in the system documentation; no on-call rotation exists in this repository, so a 03:00 runbook would be fiction. |
| 2 | 2026-11-19 | Is the sibling `services/catalog` in scope? | No. `services/catalog` is a **separate bounded context with its own implementation on `main`** (128 source files) and its own specification, `docs/specs/catalog/spec.md`; it is documented there, not here. This docset's documented system is `services/fulfillment`, and the repository context is described only as far as it touches fulfillment. |
| 3 | 2026-11-19 | Is the goods issue note (WZ) part of the delivered system? | No — out of scope (spec register `D15`): no invoicing contract exists to supply amounts or the receiver party. The label document *is* delivered. |
| 4 | 2026-11-19 | Are the warehouse and carrier integrations real clients? | Yes: real adapters behind `WarehouseStock` and `CarrierPort`, configured by properties and token. There are no simulator or CLI components, and the tests use in-process fakes (spec register `D14`). |
| 5 | 2026-11-19 | Does the customer endpoint check that the caller owns the order? | No. It accepts any token with the `fulfillment-customer` scope; `Order` carries no customer identity (spec register `D13`). Recorded as a risk, not a defect. |
| 6 | 2026-11-19 | Are collection endpoints paginated? | No. One `{"items": [...]}` envelope, no `page`/`size` (spec register `D16`). |
| 7 | 2026-11-19 | Can one order produce more than one parcel? | Not today. One parcel per processing with `ofTotal = 1` (spec register `D10`); multi-parcel packing is out of scope. |
| 8 | 2026-11-19 | What happens to the findings the branch review left open? | Accepted as recorded risk, not fixed in this pass. They are listed in `docs/specs/fulfillment/spec.md` §Open Topics: out-of-order legs, actuator surface reachable from the customer scope, one rollback test that cannot fail, pollers holding a connection across remote I/O, `@EnableRetry` with no `@Retryable`, element 09 scenario S9 untested, workstation and reprint rules Java-only, `missing[]` naming the wrong part. |
| 9 | 2026-11-19 | What is expected of the finished docset? | High quality, consistent, high value: no dangling includes, one vocabulary across the three documents, every claim traceable, and an explicit gap list rather than reassurance. |
| 10 | 2026-11-19 | Are the previous (untracked) documents to be kept? | No. The earlier catalog/fulfillment drafts were discarded; the new docset replaces them and nothing of the old set is referenced. |
