# Acceptance scenarios — catalog (service `services/catalog`)

Derived from `docs/specs/catalog/spec.md` §Prove Definition of Done (P1–P8) and `docs/prove-of-done.md`, narrowed to what a
viewer can see happen against the running service. This service has no frontend, so every scenario is driven over its own HTTP
contract (element 12) and Kafka topic; chapter 5 shows the three screens of 14/15/16 in the storybook gallery.

Recorded by `demo/scripts/record.sh` (world + take + mux). Live, in order, one chapter each:

1. **Lifecycle — draft, findings, override, publish** — an offer goes from draft to published version 1, and the quality gate is
   shown to block both the review request and the publication.
   - author creates the offer → `offerState: draft`, `offerVersion: null`
   - photographer adds media (the author's token is refused on that component)
   - author sets an availability start derived from today (`+30d`)
   - author writes a 47-character description → `quality.findings: [{rule: QP-6, severity: BLOCKING}]`
   - review request without an override → `409 FINDINGS_NEED_OVERRIDE`
   - review request with `override.findings: ["QP-6"]` → `201 requested`
   - reviewer accepts → `409 QUALITY_FINDINGS` (`prose 47 < 1000 (minProseChars)`) — an override never buys a publication
   - author writes the full description → no findings; new review → `201`; accept → `accepted`, `publishedOfferVersion: 1`
   - the offer reads `published`, version 1

2. **Privacy and visibility — drafts leak nothing, dates decide** — a draft edit publishes nothing and never leaves the content
   roles; availability start decides whether a published offer is visible.
   - the chapter-1 offer is published with an availability start a month ahead
   - a second offer is created and its draft written
   - the events topic lists one line per offer that published something — the drafted offer is absent
   - `GET /offers/{id}/draft` as `catalog-sales` → `403`, empty body
   - media + a past availability start (`-1d`), review, accept → published version 1
   - the offer list shows two published offers, one availability start behind today and one ahead

3. **Pricing — current, scheduled, activated** — the current price publishes at once, a scheduled price stays silent, the
   activation publishes `PricesChanged`; a percentage and a fixed discount are accepted on the same currency.
   - `PATCH …/pricing` with the price in force → `current.amountMinor: 30989`
   - a second entry scheduled `+5s` → `scheduled` carries it, `current` unchanged
   - the offer's events: `OfferPublished`, `PricesChanged 30989` — nothing for the scheduled entry
   - after the activation poll: `current: 29900`, `history: [30989]`, and a second `PricesChanged` on the topic
   - `10 %` + `1500 PLN` off → both in `discounts.active`

4. **Rejection and recovery** — a rejection carries a reason, returns the offer to draft, and a revised draft can be re-reviewed
   and published.
   - a third offer is drafted completely and sent to review → `201 requested`
   - reviewer `{decision: rejected, reason: …}` → `state: rejected`, reason recorded
   - the offer reads `draft`, version `null`
   - author revises the description, new review → `201`, accept → `publishedOfferVersion: 1`

5. **Views — 14, 15, 16** — the three read-model screens render: the draft screen with its findings panel, the pending-review list
   with a candidate that carries the author's override, and the pricing screen with a scheduled change and a stacked preview.
   - `draftdescriptionview--ready`, `--in-progress` (element 14)
   - `pendingreviewsview--pending-list`, `--review-with-author-override` (element 15)
   - `pricingview--scheduled-history`, `--add-discount-fixed` (element 16)

Dates in scenarios 1–4 are computed at record time (`date -v+30d`, `date -v-1d`, `date -v+5S`); no step depends on a fixed date.

## What the video cannot show, and why

- **Visibility verdict.** Element 11 is derived at read time and has exactly one consumer: the 14 read model (`OfferDraftReads`),
  which has no HTTP route — element 12 exposes offer, draft, versions, pricing and review, not a shop surface. Chapter 2 therefore
  shows the two published offers with their availability starts (the inputs of R1/R2) and chapter 5 shows the rendered verdict.
- **Tokens** arrive from the harness (`demo/run/env.sh`), so no recorded line carries a bearer token.

## How to re-run

```sh
demo/scripts/boot.sh                      # Postgres, Kafka, Keycloak realm, the service on :18090
source demo/run/env.sh                    # $BASE and one token per scope of 12
demo/scripts/record.sh                    # fresh world + both takes + chapters -> demo/catalog.chapters.mkv
demo/scripts/stop.sh                      # tear it all down
```
