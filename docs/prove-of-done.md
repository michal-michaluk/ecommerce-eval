# catalog — prove of done

Basis: `docs/specs/catalog/spec.md:160-169` §Prove Definition of Done (P1–P8) and
`docs/specs/catalog/12-catalog-http-api.md:8-14` (what the hurl pins, what it cannot pin). Every row was executed from
`services/catalog`; the exit code and the number that matters are the command's own output. Environment: Java 25.0.3, Docker
running (Testcontainers), `hurl` on the harness path, PITest 1.30.2.

**Final artifact state — one sequence, in this order.** `./gradlew clean build` (exit 0, 33 s) → **73 suites in
`build/test-results/test/`, 663 tests, 0 failures, 0 skipped**, `jacocoTestCoverageVerification` executed, JaCoCo instruction
**95.23 %** (14 911/15 658, gate 0.80) and branch **85.54 %** (692/809) in `build/reports/jacoco/test/jacocoTestReport.xml`, both
`spotbugs` tasks executed → `./gradlew pitest` (exit 0) writing `build/reports/pitest/mutations.xml` and `index.html`. The 73 XMLs
are still in place after the mutation run (PIT does not run the `test` task), so P1/P2/P4/P6/P8 are readable from the same two
paths. The P1 history below is a *different*, earlier sequence of this session — its own run is gone, and the numbers quoted in
that row's history are that run's, not these.

| Row | Command | Observed | Verdict |
|---|---|---|---|
| P1 | `./gradlew build`, then `./gradlew clean build` | History, earlier in this session: `./gradlew build` alone was `BUILD SUCCESSFUL` with **12 tasks `up-to-date`** — nothing compiled, nothing tested; the first `clean build` failed (`660 tests completed, 1 failed` — `ReadsRebuildTest.rebuildsThePendingListFromTheReviewsOwnState()`, `ReadsRebuildTest.java:76`), the retry passed 660/660 at 95.08 %. **The closing run**, after the P6/P8 work: `clean build` exit 0 in 33 s, 13 actionable tasks executed · `:jacocoTestCoverageVerification` **executed** · **663 tests, 0 failures, 0 errors, 0 skipped in 73 test classes** · JaCoCo instruction **95.23 %** (14 911/15 658, gate min 0.80), branch **85.54 %** (692/809) · `spotbugsMain`/`spotbugsTest` executed, `ignoreFailures = false` | PASS (flaky suite — Gaps) |
| P2 | `./gradlew test --tests '*ArchitectureOf*Test'` | exit 0 · 5 suites — `offer`, `pricing`, `quality`, `reads`, `review` · **30 tests** (6 each), 0 failures · 6 `@ArchTest` rules + 6 `allowEmptyShould(true)` per suite | PASS (limited — Gaps) |
| P3 | `sqt check .` | exit 0 · `SUCCESS: 247 files: 238 ok, 0 error, 9 skipped, 40 ignored · 59 violations · 5681 not evaluated` · all 59 hint/warning-level: 31 `vo.builder-when-wide`, 3 `event.polymorphic-json`, 2 `event.past-tense` (hints), 9 `function_statements`, 8 `method_lines`, 3 `method_logical_lines`, 2 `methods_per_class`, 1 `class_lines` (metrics warnings) · **zero error-severity violations** | PASS (partial — Gaps) |
| P4 | `./gradlew test --tests '*QualityPolicyTest' --tests '*PublishDecisionTest' --tests '*ActivePriceCalculationTest' --tests '*OfferVisibilityTest'` | exit 0 · scenario methods green **18 / 11 / 10 / 8** · suite totals **25 / 14 / 15 / 10** (7 / 3 / 5 / 2 non-scenario unit tests in the same classes) | PASS (drift — Gaps) |
| P5 | `./gradlew e2eHurl` | exit 0 · `Executed files: 1 · Executed requests: 106 · Succeeded files: 1 (100.0 %) · Failed files: 0` in 1 951 ms · **106 requests over 4 resource shapes** (`/offers`, `/offers/{offerId}/draft`, `/offers/{offerId}/pricing`, `/offers/{offerId}/review`) | PASS (narrowed — Gaps) |
| P6 | `./gradlew test --tests '*.ShoppingEventsOfOfferTest' --tests '*.ShoppingEventsOfPricingTest' --tests '*.DraftPrivacyTest'` | exit 0 · evidence is the three XMLs, and the gate node now asserts their counts: `build/test-results/test/TEST-shop.catalog.offer.DraftPrivacyTest.xml` **3**, `TEST-shop.catalog.offer.ShoppingEventsOfOfferTest.xml` **10**, `TEST-shop.catalog.pricing.ShoppingEventsOfPricingTest.xml` **10** — 23 tests, 0 failures, 0 skipped · the payload half is a strict comparison, `JSONAssert.assertEquals(expected, actual, true)` in `JsonAssert.isExactlyLike` (`src/test/java/shop/catalog/JsonAssert.java:25-40`), of each shipped payload against the payload of 13 written out in `ShoppingEventsFixture`/`PricingEventFixture` — no extra and no missing field · `src/test/java/shop/catalog/offer/DraftPrivacyTest.java` is the named gate for 01 RULE-4 + 13 RULE-1, three tests: (1) a real draft edit through `OfferService` adds exactly one `OfferEvent.OfferDraftUpdated` to the recorded facts and no `OfferVersionFrozen`/`OfferRemoved`, and the `EventStream` spy received no envelope of any of the three public types — with a real publication at the end that adds the frozen fact and ships exactly `OfferPublished`, so both captures are shown to observe a publication; (2) `catalog-author` reads the same offer with `200` on `/offers/{offerId}` and `/offers/{offerId}/draft` and the draft body carries the edited title and paragraph, then `catalog-sales` is refused `403` with an empty body on all three `GET` routes and `catalog-author`/`catalog-reviewer` list pages contain the offer's id but none of its title, paragraph or media url; (3) the service's own loggers (`shop.catalog`, level `ALL`) write no line carrying the draft title, paragraph, url or id while the draft is created, edited and read, with a DEBUG probe captured first to prove the capture sees DEBUG · each of the three was proven to discriminate by breaking its invariant once locally and restoring it — one-off checks, not reproducible from the repo — Gaps | PASS |
| P7 | `cd .storybook && node verify-storybook.frames.cjs` | exit 0 · executed **without the spec's two arguments** (see Gaps) · port 6010 free — `nothing answered on http://localhost:6010 — serving storybook-static on port 6010` — so the script served its own build · **27 stories**, each `PASS` with a non-empty root (4 324–10 483 chars), zero console/page errors · **7 flow chains**, all `PASS` · `QUALITY GATE PASSED` | PASS (undisclosed deviation — Gaps) |
| P8 | `./gradlew pitest` | exit 0 · `BUILD SUCCESSFUL` · `Generated 736 mutations Killed 654 (89%)` · `Mutations with no coverage 22` · `Test strength 92%` · `358 tests examined` · `Ran 4910 tests (6.67 tests per mutation)` · line coverage of the mutated classes `1224/1287 (95%)` · **per context** (killed / generated): `pricing` 221/234 **94.4 %**, `quality` 119/126 **94.4 %**, `reads` 57/62 **91.9 %**, `offer` 157/181 **86.7 %**, `review` 88/120 **73.3 %** — four of five clear the 80 % bar, `review` does not · the root package (`Scopes`) 12/13 92.3 % is the only non-context package in the target set · targets are the domain and policy classes of the five contexts, the same bundle `jacocoTestCoverageVerification` measures; the edges of a context, `tools` and `mediators` are not mutated — Gaps · `build/reports/pitest/mutations.xml` and `index.html` written · one mutant is unstable between runs: `reads/OfferVisibility.published` line 59 ended `RUN_ERROR` in the first of the three runs and `KILLED` in the other two (`reads` 56/62 vs 57/62); no `TIMED_OUT` in any | FAIL — 4 of 5 contexts ≥ 80 %, `review` at 73.3 % |

## Gaps

- **P6 — closed, and two defects fell out of it.** (1) The command was a **false pass**: `--tests '*ShoppingEventsTest'` matches
  no class (`ShoppingEventsOfOfferTest` does not end in `ShoppingEventsTest`) and a Gradle filter that matches nothing still exits
  0 — worse, the 5 `ArchitectureOf*Test` suites run under *every* filter (`--tests '*NoSuchTestAtAll*'` still runs them, 30
  tests), so the filtered command looked green while the event suites never executed. `spec.md:167` now names the two classes and
  `spec.md` §9's `prove-dod` node asserts the three XML counts, so a filter that matches nothing turns the row red.
  (2) The invariant had no named gate. `src/test/java/shop/catalog/offer/DraftPrivacyTest.java` is that gate.
- **P6 — what the gate actually proves, and what it does not.** The event clause is the fact list and the `EventStream` spy, not
  "no `OfferPublished` is recorded as a Spring event": nothing in `src/main/java` publishes those three types in-process, so only the
  spy can see them (`DraftPrivacyTest.publishesNoPublicEventWhenTheDraftIsEdited`). The log clause is the service's own loggers
  (`shop.catalog`, level `ALL`) during create + edit + read — the ORM's SQL logging of the test profile is outside it, and the
  clause is "no captured line carries the draft", not "the capture is empty". The `GET` clause is the wire for the three routes
  `catalog-sales` can reach plus the four scopes on the list; the per-route auth matrix of all five route shapes stays
  `ScopeGuardTest`'s, and those handlers are route markers — a controller logging under another package would also escape the log
  clause. The three break-and-restore checks that proved each test discriminates (publish on `OfferDraftUpdated`; draft title in the
  list payload; a draft title in a log line; and the deny-only `hasAuthority(PHOTOGRAPHER)` on `GET /offers/{id}/draft` that the
  presence control catches) were one-off: nothing in the repo reproduces them, and because the gate is an `@IntegrationTest` suite
  P8 excludes it, so no mechanical check will notice the assertions rotting.
  Scope of the payload half: the "13 schemas" are 13's payloads written out by hand in the two fixtures and compared strictly
  (`JSONAssert.assertEquals(..., true)`), not checked against a machine-readable JSON Schema — the service carries no such document,
  so a field added to a payload fails the fixture, not a schema.
- **P8 — FAIL on the criterion: one context is below the bar.** `spec.md:169` asks for ≥ 80 % "on the context classes", so the
  verdict is per context and `review` is at **73.3 %** (88/120). Four clear it: `pricing` 94.4 %, `quality` 94.4 %, `reads` 91.9 %
  (90.3 % in one of three runs — one `RUN_ERROR` on `OfferVisibility.published`), `offer` 86.7 %. The aggregate 89 % is not the
  criterion and is not used as one. What is left in `review`: **22** compact-constructor
  mutants (`Review` 6, `ReviewRequest` 5, `ReviewFinding` 4, `ReviewCandidate` 2, `OfferContent` 2, `ReviewOverride` 2,
  `PublishDecision` 1) that no test observes because no test builds an invalid review value; **7** removed
  `ReviewException.ensureValue` calls (`ReviewProcess.accept` 2, `.reject` 2, `.withdraw` 1, `.recordBlock` 1,
  `.ensureTheAttemptIsAdmissible` 1), i.e. no test asserts that each refusal is *raised*; and **2** ordinary logic mutants that are
  simply uncovered — `ReviewException.findings` line 112 (`details.size() > 1` boundary) and `ReviewException$Error.format` line 47
  (return value replaced with `""`). One `review` mutant has no coverage at all (`ReviewException.details`, line 98), so the 73.3 %
  hides one uncovered line inside its denominator.
- **P8 — the mutation targets are the domain, not the edges, and that is a stated limit.** `excludedClasses` drops the
  controllers, repositories, read projections, messaging adapters, listeners, configuration, properties and the ports that name
  them, plus `tools` and `mediators` — the same bundle `jacocoTestCoverageVerification` excludes (`build.gradle:200-211`), so P8 and
  P1 measure one class set. The consequence: what P8 does *not* prove is that the HTTP and persistence adapters are guarded by
  their tests — P5 and the 30 `@IntegrationTest` suites are that evidence. The 22 `NO_COVERAGE` mutants that remain inside the
  target set are a property of that exclusion too, not of the suite: `offer/Media.idOf`, `OfferEditor.addMedia`/`frozenVersions`,
  `review/ReviewException.details` and `pricing/PricingChanged.*` are reached only through container paths.
- **P8 — the tooling change, and how much of it is verifiable now.** (1) The coverage-phase abort: the `pitest` block carried no
  test exclusion, so PIT booted the application and replayed `e2e/catalog-http-api.hurl`; it now excludes `shop.catalog.e2e.*` and
  the 30 suites that declare `@IntegrationTest` at the start of a line — derived in `build.gradle`, so a new container suite excludes
  itself and a file that merely mentions the annotation in a javadoc is still mutated. (2) PIT 1.25.4 produced no score at all: every
  mutation unit ended `RUN_ERROR` with the minion socket closing (`EOFException`) and the host JVM writing `hs_err_pid*.log` dumps
  whose `SIGSEGV` sat in `JVM_GetRecordComponents`, right after an `Event: redefined class name=…PricingEdits$Promotion`. Those
  dumps are not in the repo (deleted); what is verifiable today is the outcome — `pitestVersion = "1.30.2"` runs the same target set
  to completion twice with identical mutant counts and no crash. The causal reading (the bytes PIT writes for a record, not the JDK
  alone, since an `Instrumentation.redefineClasses` replay of an unmodified record is safe here) is a diagnosis of that session.
- **P1 — the suite is flaky.** `pitest` no longer meets that flakiness (the container suites it used to replay are excluded), but
  `./gradlew test` still does: the first `clean build` in this session failed on
  `ReadsRebuildTest.rebuildsThePendingListFromTheReviewsOwnState()` (`ReadsRebuildTest.java:76`, `AssertionFailedError`); the
  retry passed 660/660. It is shared-state flakiness of the container suites: under PITest 1.25.4 it surfaced as
  `OfferControllerTest.listsOnePageAndThePageAfterIt()` and `CatalogHttpApiE2eTest.runsEveryRequestOfTheContractOfElement12()`,
  both green under `./gradlew test`. Row PASS is the retry's result and is recorded as such — a green P1 is not reproducible on
  demand. `./gradlew test` after this session's change: 663 tests in 73 suites, 0 failures.
- **P1 — process note.** `./gradlew build` alone returns `12 up-to-date` and would "pass" while proving nothing; the recorded
  figures come from `./gradlew clean build`.
- **P2 — the property is only proved because the packages are not empty.** `allowEmptyShould(true)` is on all six rules of all
  five suites, as `spec.md:163` demands, which also means the row would go green on five empty context packages. The evidence it
  is not vacuous is indirect: the same suites pass while `src/main/java/shop/catalog/{offer,pricing,quality,reads,review}` carry
  the classes P1's coverage and `sqt` measured.
- **P3 — partial, despite the PASS.** The spec's criterion is exit 0 only, but the row's property ("the code obeys the shape
  rules") is wider than what exit 0 proves: `9 skipped`, `40 ignored`, `5 681 not evaluated`, 4 `.storybook/*.js` files with
  "incomplete element coverage", and the 59 violations are hints/warnings `sqt` is configured never to fail on. Nothing
  error-severity was reported.
- **P4 — spec drift.** `spec.md:165` expects "18 + 11 + 10 + 8 scenarios green" and that holds, but the suites hold 25 / 14 / 15 / 10
  tests; the extra 7 / 3 / 5 / 2 are unit tests of the same policies, not scenario rows. The row is PASS either way; the spec
  number understates the suites.
- **P5 — narrowed by 12 itself.** "Every error path" means every path a client can reach: `12-catalog-http-api.md:11-12` states the
  optimistic-lock row and the unexpected `500` row cannot be reached, so 106 requests prove the reachable set and nothing more.
- **P7 — undisclosed deviation, now disclosed.** The spec's command is
  `node verify-storybook.frames.cjs http://127.0.0.1:6010 --flow flows.json`; this session ran `node verify-storybook.frames.cjs`
  with no arguments. Behaviour-equivalent — `.storybook/verify-storybook.frames.cjs:29-33` defaults the url to
  `http://localhost:6010` and `--flow` to `flows.json` beside the script — but two things differ from what the spec's command
  implies: `localhost` instead of `127.0.0.1`, and the gate **self-served** its static build because nothing answered on 6010, so
  the "point the gate at an already-running gallery" path was never exercised. Spec drift: `spec.md:168` asks for "3 flow
  chains", `flows.json` carries 7, and all 7 pass.
- **The node's own gate was a false pass in this shell — fixed and then hardened.** `grep -c '^| P' docs/prove-of-done.md`
  resolves to `~/.local/fs-compat/agent-bin/grep`, which drops the bare `|` from the pattern and returns the file's line count:
  it answered `44` on a 44-line file and `8` on an eight-line file with no match at all, while `/usr/bin/grep` answered the true
  `8`. The `prove-dod` checks in `spec.md` now count `'^[|] P[1-8] |'` with `/usr/bin/grep` and require exactly 8 — a `| P9` row
  or a dropped row fails — and they assert the 3 / 10 / 10 test counts of the three P6 suites, so a filter that matches nothing
  is red. The same false-pass class was found in P6's original Gradle filter: a check is only a check when it names something
  that exists and asserts what it found.
