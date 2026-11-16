# 12 — CatalogHttpApi

Element 12 · type: API we expose · actors: ContentAuthor, ContentReviewer, Photographer, Sales · accepted.

Executable contract: [`prototypes/catalog-http-api.hurl`](prototypes/catalog-http-api.hurl) — 34 requests over 15 method+path
shapes, `hurlfmt --check` clean. It is **not** yet self-contained (F16): it reads ten variables it never captures
(`offer_id`, `offer_with_findings`, `offer_without_media`, `offer_without_review`, `scheduled_price_id`, `started_price_id`, the four
tokens), so the implementation adds `[Captures]` plus the three fixture offers and recomputes this count. This file carries what a hurl
cannot: the auth matrix, the status ownership and the decisions behind the shape.

## Shape — four resource shapes, one edit endpoint per family

| Resource | Path | Edit endpoint |
|---|---|---|
| the offer itself — state, publish history | `/offers/{offerId}` | `PATCH` (removal only) |
| **description draft + media + availability** | `/offers/{offerId}/draft` | `PATCH` |
| prices and discounts | `/offers/{offerId}/pricing` | `PATCH` |
| review process | `/offers/{offerId}/review` | `PATCH` |

`POST` starts / creates · `PATCH` edits · `GET` reads. No verb segments, no `/api` prefix (`http.resource-path`).

| Endpoint | Method | Intent |
|---|---|---|
| `/offers` | `POST` | create the offer with title + identifiers and an empty draft |
| `/offers` | `GET` | list offers (paged) |
| `/offers/{offerId}` | `GET` | offer state, current version, visibility |
| `/offers/{offerId}` | `PATCH` | `{offerState: "removed"}` — removal is a state edit, not a draft edit |
| `/offers/{offerId}/versions` | `GET` | publish history — versions, `publishedAt`, `publishedBy`, `copiedFrom` (paged, D31) |
| `/offers/{offerId}/draft` | `GET` | draft: description, media, availability, `quality.findings` |
| `/offers/{offerId}/draft` | `PATCH` | **the single draft edit**: `description`, `media`, `availabilityStart` |
| `/offers/{offerId}/versions` | `POST` | revert — create a new published version from an older one |
| `/offers/{offerId}/pricing` | `GET` | current, scheduled, history, discounts |
| `/offers/{offerId}/pricing` | `PATCH` | **the single pricing edit**: `prices`, `discounts`, `removePriceIds`, `removeDiscountIds` |
| `/offers/{offerId}/review` | `POST` | start the review (freezes the candidate); body `{override?: {findings[]}}` — the author's push past the gate, recorded on the candidate (04 RULE-8) |
| `/offers/{offerId}/review` | `GET` | review with candidate and findings |
| `/offers/{offerId}/review` | `PATCH` | **the single review edit**: `decision: accepted \| rejected \| withdrawn`, plus `reason` (required for `rejected`). The `override` component belongs to the **request** (`POST`) only — acceptance never carries
one (F15, D27) |
| `/reviews` | `GET` | pending reviews list for reviewers (paged) |

## Authentication and authorization

OAuth 2.0 / OIDC bearer JWT via the resource server (`docs/arch/microservice-java-spring/security.md`); nothing is public except
the existing actuator paths. Scopes: `catalog-author`, `catalog-reviewer`, `catalog-photographer`, `catalog-sales`.

| Endpoint | Method | Scope |
|---|---|---|
| `/offers` | `POST` | `catalog-author` |
| `/offers`, `/offers/{offerId}` | `GET` | `catalog-author`, `catalog-reviewer` |
| `/offers/{offerId}` | `PATCH` | `catalog-author`, `catalog-reviewer` (removal) |
| `/offers/{offerId}/draft` | `PATCH` | `catalog-author`, `catalog-photographer` — **by component**, see below |
| `/offers/{offerId}/draft` | `GET` | `catalog-author`, `catalog-reviewer`, `catalog-photographer` |
| `/offers/{offerId}/versions` | `GET` | `catalog-author`, `catalog-reviewer` |
| `/offers/{offerId}/versions` | `POST` | `catalog-reviewer` |
| `/offers/{offerId}/pricing` | `GET`, `PATCH` | `catalog-sales` |
| `/offers/{offerId}/review` | `POST` | `catalog-author` |
| `/offers/{offerId}/review` | `GET` | `catalog-author`, `catalog-reviewer` |
| `/offers/{offerId}/review` | `PATCH` | `catalog-reviewer` (withdrawal: `catalog-author`, own request only) |
| `/reviews` | `GET` | `catalog-reviewer` |

### Field-level scope inside `PATCH /offers/{offerId}/draft`

One draft endpoint, two writers, so scope is enforced **per component** — anything outside the caller's set is
`403 FIELD_NOT_IN_SCOPE`.

| Component | `catalog-author` | `catalog-photographer` |
|---|---|---|
| `description` (title, identifiers, parameters, blocks) | ✅ | ❌ |
| `availabilityStart` | ✅ | ❌ |
| `media` (add / remove / reorder / primary) | ❌ | ✅ |

`PATCH /offers/{offerId}` has a single component, `offerState: "removed"`, allowed for both content roles; `catalog-sales`
and `catalog-photographer` get `403` on it. A wrong scope is always `403`, never `404`.

## Status ownership

Per `http-adapter.md`, five failure classes plus authorization, one owner each; this API adds no seventh:

| Failure | Status | Owner | Body |
|---|---|---|---|
| malformed JSON, invalid command record (bad GTIN, bad basisPoints), unknown PATCH component | `400` | framework `@Valid` | `errors[]` with `field` |
| unknown offer / review | `404` | handler | empty |
| caller lacks the scope, or the draft component is outside it | `403` | framework security / one advice | `{reason: "FIELD_NOT_IN_SCOPE"}` for the component case, empty body otherwise |
| state forbids the command (`MEDIA_PRIMARY_REQUIRED`, `QUALITY_FINDINGS`, `PRICE_ALREADY_STARTED`, `DISCOUNT_ALREADY_STARTED`, `DISCOUNT_CURRENCY_NOT_PRICED`, `REVIEW_ALREADY_OPEN`, `REVIEW_NOT_OPEN`, `OFFER_REMOVED`, `AVAILABILITY_START_MISSING`, `REMOVAL_NEEDS_PUBLICATION`) | `409` | one advice | `{reason, findings?}` |
| optimistic-lock loss | `409` | one advice | retry hint |
| anything unexpected | `500` | framework | empty (`include-message: never`) |

`reason` values are the contract; message strings are not (`http.rejection-status`).

## Decisions

- **D13 the draft is its own resource** — `/offers/{offerId}/draft` carries description + media + availability, one `PATCH`, so the
  editor never mixes content edits with an offer state change. `/offers/{offerId}` keeps the offer's own state (removal) and history.
- **D13a field-level authorization inside the draft PATCH** — the author owns `description` and `availabilityStart`, the photographer owns
  `media`; `403 FIELD_NOT_IN_SCOPE` otherwise. Without it, a photographer could rewrite the description through the same endpoint.
- **D13b removal is a state edit** on `/offers/{offerId}`, not a draft edit: it is not content, and it must stay unavailable to the
  photographer and to sales.
- **D14 write endpoints return the aggregate/draft snapshot, reads return the read model** (`http.no-persistence-type-in-api`): the draft
  `PATCH` answers with the draft plus `quality.findings`; `GET /reviews` answers from the projection only.
- **D15 acceptance publishes** (D5) and is idempotent per review (09 R8): replaying `decision: accepted` returns the same
  `publishedOfferVersion`.
- **D16 collections are paged with required `page` and `size`** (`http.required-paging`); a missing one is `400`. `page >= 0`,
  `1 <= size <= 100`, anything else `400` — the bound exists so a client cannot ask for the whole table.
- **D17 media is referenced, not uploaded** — `{kind, format, position, primary, url}` appended in the `media` component. Q12 (formats,
  size limits) must be answered before implementation validates anything.
- **D18 `PATCH` means "these components, nothing else"** — absent component = not part of the request (`http.null-means-absent`); an
  unknown component is `400`, so a typo can never be silently ignored.
- **D19a `201` on create is expressed with `@ResponseStatus(HttpStatus.CREATED)`** on the handler, not with `ResponseEntity` — `http.no-response-entity` still holds, and the `Location` header comes from the created-response advice. Asserting `201` in the hurl is therefore reachable.
- **D19 revert is `POST /offers/{offerId}/versions`** — it creates a new version instead of editing the current one (contrast D13b).

## Open questions carried

- **Q8** (1 product : 1 offer) — paths are `/offers/{offerId}`; a marketplace shape would need `/products/{productId}/offers/{offerId}`.
- **Q11** removal is terminal: any later draft edit answers `409 OFFER_REMOVED` (asserted in the hurl).
- **D49 reasons must have a producer** — every `reason` in the table above is emitted by a real domain refusal; the wire vocabulary is
  the domain's, never a second list. Concretely: `MEDIA_IMAGE_MISSING` and `MEDIA_PRIMARY_AMBIGUOUS` replace the invented
  `MEDIA_PRIMARY_REQUIRED`, and `QUALITY_FINDINGS` is emitted by the review refusal that carries the blocking findings (D27/D38). The
  prototype `catalog-http-api.hurl` must assert the produced names.
- **D50 `WITHDRAWAL_NEEDS_REQUESTER` is `409`, not `403`** — it is an identity/state rule (04 RULE-6), not a scope rule; 12's auth matrix
  lists it as a reviewer capability only because a reviewer happens to be the other party.
- **D31 identifiers** — the offer has one identifier. HTTP exposes it as `offerId` (`OFR-` + 8 uppercase alphanumerics, server
  generated); the events of 13 and the instances of 01/02/03 call the same value `productId`. There is no second, derived product id.
  `GET /offers/{offerId}/versions` is the missing publish-history read (F17), and it is also what a revert picks `copyFrom` from.
- **D35 the clock is injected** — `Clock` is the only source of "now" (`AppConfiguration.clock()`); the business date is
  `ZonedDateTime.now(clock).withZoneSameInstant(catalog.timezone)`. Tests and the hurl never depend on the wall clock: the harness
  passes run-relative dates (`availabilityStart` in the future and in the past) as hurl variables (F9).
- `PRICE_ALREADY_STARTED` is **03 RULE-8**, not RULE-7: a started entry may be edited (closed by a new one) but never deleted.
- **Q12** media `format` is accepted as sent, unvalidated.
