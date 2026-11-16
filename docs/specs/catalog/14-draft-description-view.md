# 14 — DraftDescriptionView

Element 14 · type: UI view · actor: **ContentAuthor** · accepted.

Where the author writes the draft and sees, continuously, what the quality policy still rejects.

**Gallery (click-through):** `services/catalog/.storybook/` — isolated Storybook 8 (`@storybook/html-webpack5`), never touching the
service's Gradle build or the repo root. Run it with `cd services/catalog/.storybook && npm install && npm run storybook`
(`http://localhost:6006`, we used `6010` because another project's Storybook holds `6006`). Stories:
`Catalog/DraftDescriptionView` → `Empty`, `In Progress`, `Ready`, `Review Requested`, wired with click-through navigation.

**Static snapshot:** [`prototypes/draft-description-view.html`](prototypes/draft-description-view.html) — the same four screens as one
hash-routed file, no toolchain needed. Screenshots: [empty](prototypes/shot-draft-empty.png) ·
[in progress](prototypes/shot-draft-in-progress.png) · [ready](prototypes/shot-draft-ready.png) ·
[requested](prototypes/shot-review-requested.png).

**Quality gate — passed.** `node verify-storybook.frames.cjs http://127.0.0.1:6010 --flow flows.json` →
4 stories PASS (root 3 960–4 404 chars), flow `empty → in-progress → ready → review-requested` PASS, `QUALITY GATE PASSED`.
The script is a local adaptation of `specification/references/verify-storybook.cdp.js`, which reports every story as empty on
Storybook 8: it looks for `#storybook-root` in the manager page, while SB8 renders the preview in an `iframe`. The adaptation
resolves the preview frame and checks the manager URL for flow steps (`?path=/story/…`, not a hash).

Case C per `ui-prototype.md`: backend-only repo, no design system, so the gallery is the isolated low-fi one and the durable artifacts
are the stories plus the screenshots.

## Screens

| # | Screen | State of the draft | Purpose |
|---|---|---|---|
| 1 | `draft-empty` | just created, nothing filled | show that empty fields do not block editing, only publishing |
| 2 | `draft-in-progress` | description typed, media added, findings remain | show the findings panel driving the author forward |
| 3 | `draft-ready` | findings empty | the only state where the review request is enabled |
| 4 | `review-requested` | candidate frozen, review open | confirmation plus the withdraw path |

Navigation is a real click-through: `draft-empty → draft-in-progress → draft-ready → review-requested → draft-in-progress`
(withdraw). Every button lands on the next state, none is decorative.

## What the view renders

- **identity** — title (editable, QP-1 length counter), `productNumber`, `eanGtin` (check-digit hint), `categoryPath` (read-only, from creation).
- **parameters** — one row per configured parameter of the matched category (08 Q1c), each with a `valueType`-appropriate input; a
  missing required one is named in the findings, not silently empty.
- **description** — ordered block list (`heading`, `paragraph`, `bulletList`), with the running prose counter (`minProseChars`).
- **media** — thumbnails in `position` order with the primary marked; **read-only here** (the photographer owns it, D13a), with a
  visible note saying so rather than a disabled button without explanation.
- **availabilityStart** — date field; the label states that the offer stays hidden until that date (11 R1/R2).
- **quality panel** — 08 findings grouped `blocking` / `warning`, with the rule code shown so a reader can trace it to the spec.

## Action enablement

| Action | Enabled when | Blocked reason shown |
|---|---|---|
| edit description / availability | `offerState = draft` **or** a review is open | `OFFER_REMOVED` (02 RULE-6) — an open review never blocks editing, the candidate is frozen (04 RULE-3) |
| request review | findings contain no `blocking` **and** ≥1 media with a primary (02 RULE-1) | the blocking rule codes, one line ("QP-6: 620 / 1000 znaków") |
| withdraw request | `review.state = requested` and caller is the requester (04 RULE-6) | — |
| edit media | never (photographer scope) | "zdjęcia dodaje fotograf" |
| publish | never (reviewer scope, F4) | not rendered at all |

A disabled action **always** carries its reason inline — no silent grey button (`ui-prototype.md` quality rule).

## States and edge cases the view must survive

| Case | Behaviour |
|---|---|
| draft with 0 findings | green summary, review request enabled |
| draft with only `warning` findings | review request enabled, warnings listed and non-blocking (09 R1) |
| review opened by another tab while editing | edits keep working; the open review keeps reviewing its frozen candidate (04 RULE-3) |
| offer removed | read-only view, banner "oferta usunięta", no edit affordance |
| unknown product | whole-screen empty state, no partial form |
| prose exactly at the threshold (1000) | passes; the counter turns neutral, not green — the boundary is inclusive (08 QP-6) |

## Decisions

- **D20 the findings panel is the primary affordance**, not a modal at submit time: 08 computes on every edit and the panel explains each
  blocked action inline. This is the board's "you can see immediately what is missing" (segment 33) turned into a screen rule.
- **D21 the quality counter is a total, not a per-block limit** — the panel shows one number (`prose / minProseChars`), so a single long
  paragraph cannot look compliant while 08 rejects it.
- **D22 media is read-only with an explanatory note**, never a disabled control without text (D13a consequence).

## Not in scope for this view

Pricing (16), pending reviews (15), publishing (reviewer), media upload (photographer), ratings and reviews (DF1).
