# Prove of done — fulfillment

Run: 2026-11-19, branch `feat/fulfillment-spec`, service `services/fulfillment`, tree after the review blockers B1/B2 and gap G1 were fixed.
Every row below is the corresponding row of [`specs/fulfillment/spec.md`](specs/fulfillment/spec.md) §Prove Definition of Done.
`hurl:NN` is a line in `services/fulfillment/e2e/fulfillment-api.hurl`, executed by `./gradlew e2eHurl` (48 requests).

| # | Row | Command or evidence | Observed | Result |
| --- | --- | --- | --- | --- |
| 1 | service builds, static analysis and coverage | `cd services/fulfillment && ./gradlew build` | `BUILD SUCCESSFUL`, 502 tests, 0 failed, 0 skipped, JaCoCo 96.36 % INSTRUCTION / 85.76 % BRANCH, SpotBugs 0 | PASS |
| 2 | architecture rules | `./gradlew test --tests '*Architecture*'` | `ArchitectureTest` tests=38, failures=0, errors=0 | PASS |
| 3 | domain/architecture style gates | `sqt check .` | exit 0, 213 files, 0 errors, 18 non-error notes (path hints, builder hints, in-band metric warnings) | PASS |
| 4 | the whole HTTP contract of element 11 | `./gradlew e2eHurl` | `Success`, 48 requests, `Failed files: 0 (0.0%)` | PASS |
| 5 | spec id namespace | `python3 docs/specs/fulfillment/prototypes/check-ids.py` | 18 files, 87 scoped ids, 0 violations | PASS |
| 6 | one employee per order | `hurl:131` — a second claim by another employee | `$.error == "ALREADY_ASSIGNED"` | PASS |
| 7 | an order is never processed twice | `hurl:59-62` — the same `orderHash` and the same content again | `HTTP 200`, `$.duplicate == true`, same `processingId`; `hurl:79` proves the mirror case (same hash, different content) answers `$.error == "ORDER_HASH_CONFLICT"` | PASS |
| 8 | pick list carries product, quantity, location | `hurl:176-178` | `$.lines[0].productNumber == "75423"`, `$.lines[0].quantity == 1`, `$.lines[0].locations count > 0` | PASS |
| 9 | one reservation on the claim, one goods issue at readiness | `AssignmentServiceTest:43` `reservesEveryPositionProductOnceOnAGrantedClaim`; `ReadinessControllerTest:128-135` | `reserve` recorded once per position product; after one granted readiness `issuesTaken()` +1 and `reservation.issued()` is true | PASS |
| 10 | every position confirmed by scanning, idempotently | `hurl:187-197` — the same confirmation twice | `$.positions[0].state == "picked"` both times, `$.state == "picking"` | PASS |
| 11 | readiness refused while a position is open | `hurl:281-283` | `$.error == "NOT_READY"`, `$.reasons[0] == "notPacked"` | PASS |
| 12 | the parcel has a carrier identifier before the first leg | `hurl:224-225` | `$.parcel.waybill isString` and `!= ""` | PASS |
| 13 | every delivery leg reaches the customer view | `hurl:455-458` | `$.state == "readyForDelivery"`, `$.legs count > 0`, `$.assignee not exists`, `$.issue not exists` | PASS |
| 14 | an issue holds the whole order and blocks readiness | `hurl:297-328` | `$.orderState == "onHold"`; scan → `409 ON_HOLD`; readiness → `$.error == "NOT_READY"`, `$.reasons[0] == "onHold"` | PASS |
| 15 | a hold is lifted only with zero open issues | `hurl:354-373` | one open issue → `$.error == "ISSUES_REMAIN"`; all closed → `HTTP 200`, `$.state == "picking"`, `$.openIssues == 0` | PASS |
| 16 | the issue dashboard is read-only and resolver-scoped | `hurl:335-345` | resolver `200` with `$.items[0].issueId isString`, `$.items[0].orderState == "onHold"`; employee token `HTTP 403` | PASS |
| 17 | no money anywhere | `rg -n "(BigDecimal\|price\|amount\|currency\|vatRate\|gross\|netValue)" -g '*.java' services/fulfillment/src/main/java` | 12 matching lines: 9 javadoc statements of absence, 3 `BigDecimal` in `InvenTreeWarehouseClient` (one import, two `valueOf(...)` converting integer stock units); no field, parameter or record component | PASS |

## Summary

- proven: **17/17** · failed: 0 · not proven: 0
- Row 9 cannot be seen over HTTP (a port call leaves no trace in a response), so it is proven from the fakes' ledger: the claim side in `AssignmentServiceTest`, the readiness side in `ReadinessControllerTest`.
- **What this audit changed:** 17 `exists` assertions were replaced by typed ones (`isString` + `!= ""`, `count > 0`), because hurl's `exists` also passes on `""`, `[]` and `null` — rows 8, 11, 12, 13 and 16 were previously satisfiable by an empty value. The suite passes 48/48 after the hardening, which is what makes those rows non-empty claims.
- **Corrections made after an independent review of this document:** row 1's counts were re-observed on a full build (the earlier number came from a run whose JaCoCo report was later overwritten by a filtered `--tests` run), and row 17's command was re-derived (its alternation was escaped into literal pipes, which under-counted the matches).

## Known gaps this document does not claim

- `WarehouseStock.issue` is called and the note stored, but no read endpoint serves it yet (owned gap in `services/fulfillment/docs/phase-1-decisions.md`).
- Element 05 (goods issue note / WZ) is out of scope (`spec.md` D15).
- Carrier status is polled; no row asserts a latency, because no source fixes one (`R-14-1`).
- Element 09 scenario S9 (a delivery leg arriving while the order is `onHold`) has no test and no resume-time re-application — recorded as a gap by the branch review, not proven here.
