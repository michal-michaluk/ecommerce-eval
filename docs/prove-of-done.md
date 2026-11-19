# Prove of done — fulfillment

Run: 2026-11-19 · branch `feat/fulfillment-spec` · service `services/fulfillment` · commit `cae383d` + the assertion hardening below

Every row is the corresponding row of [`specs/fulfillment/spec.md`](specs/fulfillment/spec.md) §Prove Definition of Done.
`e2e/…hurl:NN` is a line in `services/fulfillment/e2e/fulfillment-api.hurl`, run by `./gradlew e2eHurl`.

| # | Row | Command or evidence | Observed | Result |
| --- | --- | --- | --- | --- |
| 1 | service builds, static analysis and coverage | `cd services/fulfillment && ./gradlew build` | `BUILD SUCCESSFUL`, 489 tests, 0 failed, JaCoCo 96.26 % INSTRUCTION, SpotBugs 0 | PASS |
| 2 | architecture rules | `./gradlew test --tests '*Architecture*'` | `ArchitectureTest` tests=38, failures=0 | PASS |
| 3 | domain/architecture style gates | `sqt check .` | exit 0, 213 files, 0 errors (7 path hints, 2 builder hints, 3 in-band metric warnings) | PASS |
| 4 | the whole HTTP contract of element 11 | `./gradlew e2eHurl` | `Success`, 46 requests, `Failed files: 0 (0.0%)` | PASS |
| 5 | spec id namespace | `python3 docs/specs/fulfillment/prototypes/check-ids.py` | 18 files, 87 scoped ids, 0 violations | PASS |
| 6 | one employee per order | `e2e/…hurl:112-114` — second claim by another employee | `HTTP 409`, `$.error == "ALREADY_ASSIGNED"` | PASS |
| 7 | an order is never processed twice | `e2e/…hurl:59-62` — same `orderHash` again | `HTTP 200`, `$.processingId == "{{processingId}}"`, `$.duplicate == true` | PASS |
| 8 | pick list carries product, quantity, location | `e2e/…hurl:159-161` | `$.lines[0].productNumber == "75423"`, `$.lines[0].quantity == 1`, `$.lines[0].locations count > 0` | PASS |
| 9 | one reservation on the claim, one goods issue at readiness | `AssignmentServiceTest:43` `reservesEveryPositionProductOnceOnAGrantedClaim`; `ReadinessControllerTest:128-135` | `reserve` recorded once per position; `issuesTaken()` +1 and `reservation.issued() == true` after one granted readiness | PASS |
| 10 | every position confirmed by scanning, idempotently | `e2e/…hurl:167-180` — the same confirmation twice | `HTTP 200` twice, `$.positions[0].state == "picked"` both times | PASS |
| 11 | readiness refused while a position is open | `e2e/…hurl:253-255` | `HTTP 409`, `$.error == "NOT_READY"`, `$.reasons count > 0` | PASS |
| 12 | the parcel has a carrier identifier before the first leg | `e2e/…hurl:203-207` | `HTTP 200`, `$.state == "readyForDelivery"`, `$.parcel.waybill isString`, `!= ""` | PASS |
| 13 | every delivery leg reaches the customer view | `e2e/…hurl:422-428` | `HTTP 200`, `$.legs count > 0`, `$.assignee not exists`, `$.issue not exists` | PASS |
| 14 | an issue holds the whole order and blocks readiness | `e2e/…hurl:267-302` | `$.orderState == "onHold"`; scan → `409 ON_HOLD`; readiness → `409 NOT_READY` with `$.reasons[0] == "onHold"` | PASS |
| 15 | a hold is lifted only with zero open issues | `e2e/…hurl:326-350` | one open issue → `409 ISSUES_REMAIN`; all closed → `200`, `$.state == "picking"`, `$.openIssues == 0`, `$.issue == null`; unknown id → `404 ISSUE_UNKNOWN` | PASS |
| 16 | the issue dashboard is read-only and resolver-scoped | `e2e/…hurl:306-319` | resolver `200` with `$.items[0].issueId` / `.kind` and `$.items[0].orderState == "onHold"`; employee token `HTTP 403`; after the lift the row is gone | PASS |
| 17 | no money anywhere | `rg -n 'BigDecimal\|price\|amount\|currency\|vatRate\|gross\|netValue' services/fulfillment/src/main/java` | 18 matches: 15 are javadoc stating the absence, 2 are `BigDecimal.valueOf(...)` converting integer stock units in `InvenTreeWarehouseClient:360,377`, 1 is the word "control"; no field, parameter or record component | PASS |

## Summary

- proven: **17/17** · failed: 0 · not proven: 0
- Ledger for row 9: `AssignmentServiceTest` covers the claim side (`reservations.recorded()`), `ReadinessControllerTest` the readiness side (`issuesTaken()` delta + `reservation.issued()`), because the fake's call log is not visible over HTTP.
- **Assertion hardening applied during this audit:** 17 `exists` assertions were replaced by typed ones (`isString` + `!= ""`, `count > 0`). `exists` passes on `""`, `[]` and `null`, so rows 8, 11, 12, 13 and 16 were previously provable by an empty value; after the change the suite proves the values are non-empty, and it still passes 46/46.

## Known gaps this document does not claim

- `WarehouseStock.issue` runs but the returned `IssueNote` is not yet served by any read endpoint (recorded as an owned gap in `services/fulfillment/docs/phase-1-decisions.md`).
- Element 05 (goods issue note / WZ) is out of scope (`spec.md` D15).
- Carrier and warehouse status signals are polled; no row here asserts a latency, which no source fixes (`R-14-1`).
