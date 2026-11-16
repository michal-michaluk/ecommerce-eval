# 04 — ReviewProcess

Element 04 · type: business concept · role: **Process** (stateful, owns the review lifecycle) · accepted.

Board flow: `request review` → `description pending review` → reviewer validates against the quality policy → publish.

## Instance A — accepted with a clean policy

```yaml
reviewId: RV-12
productId: PRD-75423
state: accepted
requestedBy: author.nowak
requestedAt: "2025-06-14T09:10:00+02:00"
reviewedBy: reviewer.kowalska
reviewedAt: "2025-06-15T09:58:00+02:00"
candidate:
  description: { title: "LEGO Star Wars SMART Play X-Wing…", ids: {productNumber: "75423", eanGtin: "5702017817705"}, blocks: [...] }   # the value, frozen
  media: [ {id: M-01, position: 1, primary: true}, {id: M-02, position: 2}, {id: M-03, position: 3} ]
  availabilityStart: 2026-07-01
findingsAtRequest: []
override: null            # set only when the author pushed the review despite findings
```

## Instance B — rejected, findings recorded, candidate kept for audit

```yaml
reviewId: RV-13
productId: PRD-75375
state: rejected
requestedBy: author.zielinski
reviewedBy: reviewer.kowalska
findingsAtRequest: [ { rule: RULE-1, field: description.title, finding: missing } ]
reason: "Brak EAN i niepełny opis"
candidate: { … }
```

## Lifecycle

`requested → accepted | rejected | withdrawn`. `accepted`, `rejected`, `withdrawn` are terminal; a new attempt is a
new `reviewId`.

## Domain rules

- **RULE-1** at most one open review per product; a request while one is open is refused (conflict in 12).
- **RULE-2** the reviewer must differ from the requester (segment 25: "other person responsible for the content management").
- **RULE-3** the candidate is frozen at request time: later draft edits do not change what is being reviewed — the only way "what was reviewed" and "what gets published" can be the same content, since drafts carry no version number.
- **RULE-4** acceptance is the publication trigger (F4): `accepted` → 09 PublishDecision → freeze of the offer version from the candidate; the freeze happens exactly once per `reviewId`.
- **RULE-5** rejection records a free-text reason and returns the product to `draft`; the candidate stays for audit.
- **RULE-6** withdrawal is allowed only by the requester and only while `requested`.
- **RULE-7** a review never carries over: after rejection the next request creates a new candidate and a new `reviewId`.
- **RULE-8** the requester may push a review even while 08 reports findings, but only with `override` set, recording actor + timestamp + the findings it ignores (transcript seg. 33). The reviewer has **no** override: acceptance cannot be forced past the gate (09 R2, F15).

## Instance diff — requested → accepted → published

```diff
  state: requested
- reviewedBy: null
+ reviewedBy: reviewer.kowalska
+ reviewedAt: "2025-06-15T09:58:00+02:00"
- state: requested
+ state: accepted
+ publishedOfferVersion: 1
```

## Domain events

```yaml
- ReviewRequested:  { productId: PRD-75423, reviewId: RV-12 }
- ReviewAccepted:   { productId: PRD-75423, reviewId: RV-12, override: null }
- ReviewRejected:   { productId: PRD-75375, reviewId: RV-13, reason: "Brak EAN i niepełny opis" }
- ReviewWithdrawn:  { productId: PRD-75423, reviewId: RV-12 }
```

## Decisions taken at acceptance

- **Q15 → D4 candidate timing:** candidate frozen at request (RULE-3).
- **Q16 → D5 exit path:** acceptance publishes immediately; no separate publish command (F4).
