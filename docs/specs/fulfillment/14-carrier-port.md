# 14 — Fulfillment → Carrier

**Role:** **Driven port / adapter boundary**, carrier-agnostic — an *external* port like element 13's: declared by the
consumer, implemented by a protocol adapter, `public` because both the fake and the real adapter implement it (the arch
taxonomy's three kinds are primary, package-private secondary and contract — `docs/arch/microservice-java-spring/ports.md:6-13`
— and a carrier outside the SUD is none of them; recorded for the Step 6 review). `CarrierPort` is the interface
`fulfillment` needs; DPD Polska is the **first adapter** behind it, never a name in a type, a topic or a method
(`spec.md` D3, `UND-15`). The board's `dhl` sticky (`eventstorming.json:179`) and the DPD label artifact
(`consignment-note.json:8-9`) are both facts about the same lane; neither names this port (KB
`integrations/spedition-pl/README.md:19-36`).

**Definition:** the four things the business asks of *any* carrier, each lifted from the narration and matched to a
capability rather than to an endpoint (KB `integrations/spedition-pl/README.md:38-45`):

| # | Obligation | Business wording | Port operation |
| --- | --- | --- | --- |
| O1 | register the parcel before it physically exists and get an identifier back | "on the very beginning … we will get some vendor ID, so provider ID, and we want to attach it to that order processing" `[seg 115-116]` | `register` |
| O2 | produce the printable document that travels with the parcel | the label artifact (`consignment-note.json:1-4`) | `label` |
| O3 | ask the carrier to collect on "ready for delivery" | "we can make a request to deliver it, a company that the parcel is ready for delivery" `[seg 122-123]` | `requestCollection` |
| O4 | report status per leg until delivery | "the full every leg in the transporting chain, we will get some notification, and at the end they will notify us that customer picked it" `[seg 124-126]` | `status` (polled) |

The transaction of a parcel between us and the carrier — endpoints, modules, payload schemas, header names, document
formats, status codes — is **adapter detail**, deferred by `spec.md` D2 to the carrier adapter spec and documented in the
KB (`integrations/spedition-pl/dpd-polska/`, nine files). This element fixes only our side.

## Contract

```java
/** Consumer-side contract: what the delivery of one parcel needs from any carrier. */
public interface CarrierPort {

    Registration register(RegistrationRequest request);

    LabelDocument label(LabelRequest request);

    CollectionHandle requestCollection(CollectionRequest request);

    List<LegObservation> status(CarrierIdentity identity);
}
```

Value objects are ours, and every type in the signatures above is one of them:

| Value object | Values | Notes |
| --- | --- | --- |
| `ParcelReference` | our key, unique, ≤ 50 chars | the only correlation key, RULE-2 |
| `RegistrationRequest` | sender, receiver **or** pickup point, weight per parcel, `ofTotal`, our reference, opaque service / COD / declared-value values | sender and pickup address are **configuration**, not order content (`spec.md` F4) |
| `Registration` | `ParcelReference` → `CarrierIdentity`, plus `ofTotal` and the per-parcel sequence | the identity is only ever assigned from a response |
| `CarrierIdentity` | an opaque carrier-assigned string (waybill, tracking number, `numerNadania`, `shipmentId`) | never invented, never parsed off paper |
| `Leg` | L1–L6 with our names | element 03 |
| `LabelRequest` | the parcel(s) to render, by our reference or carrier identity, plus format and layout choices | a render, never a registration |
| `LabelDocument` | bytes + the format they were requested in | never read back for data (element 03 Q-03-1) |
| `CollectionRequest` | **one parcel**, named by our reference **and** carrier identity | what element 08 hands over per parcel (element 03 `markReady`); the pickup address is configuration, so it is not request data |
| `CollectionHandle` | the carrier's opaque booking handle | stored so the booking can be changed or cancelled by an adapter that offers it |
| `LegObservation` | `leg`, carrier timestamp, raw carrier status, raw payload | the port's answer, mapped from whatever the carrier calls it (`Q-14-1`) |

## Two identities, never merged

The rule the port encodes, verbatim from the KB (KB `integrations/spedition-pl/dpd-polska/waybill-numbers.md:69-79`):

| Identity | Owner | Lifetime | In the port |
| --- | --- | --- | --- |
| our parcel reference | us | forever | **input** to `register`; unique; never derived from a carrier number |
| the carrier identity (waybill / tracking number / `numerNadania` / `shipmentId`) | the carrier | from registration until the parcel closes | **output** of `register`; only ever assigned from a response; never invented, never parsed off a printed label |

The label artifact proves why this cannot be relaxed: it carries no order reference, no barcode value and no parcel number
(`consignment-note.json:54`), so correlation exists only in the response (element 03 RULE-2, element 03 Q-03-1; KB
`waybill-numbers.md:73-79`).

## Operations

| # | Operation | Input | Output | Side effect | Idempotency |
| --- | --- | --- | --- | --- | --- |
| 1 | `register` | `RegistrationRequest` | `Registration` — one `CarrierIdentity` per parcel, plus the sequence the carrier will print | **a carrier identity comes into existence**: numbers are consumed and, for the first adapter, cannot be released | keyed by our `ParcelReference`, which is unique and never reused; a blind retry is *refused as a duplicate*, not silently deduplicated (KB `waybill-numbers.md:73`, `:114-122`; `Q-14-2`) |
| 2 | `label` | `LabelRequest` | `LabelDocument` | none: a render, not a registration | idempotent — re-rendering from stored data never re-registers (element 03, `reprint`) |
| 3 | `requestCollection` | `CollectionRequest` — one parcel | `CollectionHandle` | **books a pickup** with the carrier | **not idempotent**: a repeat books a second pickup, so at most one open collection per parcel (RULE-4) |
| 4 | `status` | `CarrierIdentity` | `List<LegObservation>` | none — a read | idempotent; re-polling the same leg is a no-op and only **transitions** are published (KB `integrations/spedition-pl/carriers/notification-mechanisms.md:80-99`) |

### Status per leg — polled, never pushed

| Fact | Evidence |
| --- | --- |
| The business expects a notification per leg, six of them, ending "customer picked it" | `[seg 124-126]` |
| The first adapter publishes **no** push mechanism — no webhook, no callback, no subscription, and no status path in its REST surface | KB `integrations/spedition-pl/dpd-polska/status-polling.md:8-22` |
| Among the Polish carriers only one publishes status webhooks; the rest are poll-only, and for the first adapter the absence of push is an absence of *documentation*, recorded as a negative finding | KB `integrations/spedition-pl/carriers/notification-mechanisms.md:8-28`, `status-polling.md:17-20` |
| The first two legs are ours to know without asking anybody: registration succeeded, collection requested | KB `status-polling.md:62-68` (S1, S2) |

So `delivery state notification` (`eventstorming.json:133`, `:179`) is an event **we** raise after a poll returns something
new — the external interaction the board lists under `dhl` is a scheduled outbound call, not an inbound one (KB
`status-polling.md:22-32`). The port speaks **our** six legs — L1 registered, L2 provider collected, L3 assigned for
pickup, L4 picked up, L5 in the sorting facility, L6 customer picked it (element 03) — never a carrier's status vocabulary,
which is unmapped (`Q-14-1`).

### No money

`cashOnDelivery` and `declaredValue` travel as **opaque values** on `RegistrationRequest` and leave no trace in this
context: not stored, not compared, not formatted (spec.md D5, element 03 RULE-7). A carrier service that changes what is printed
or what the carrier charges is selected by the caller, not evaluated here (services and their exclusion rules are adapter
and checkout material: KB `integrations/spedition-pl/dpd-polska/services-and-options.md:9`, `:26`, `:41`, `:112-127`).

### Deferred to the adapter spec (spec.md D2)

Endpoint and module split, authentication (`x-dpd-fid` master FID, HTTP Basic, per-stage hosts, secret storage — KB
`sandbox-and-credentials.md:55-71`), label formats and printer geometry, the second document of the first adapter (the
handover protocol), service codes and their exclusion rules, the carrier's own master data that prints on paper but never
appears in JSON (KB `dpd-polska/labels-and-documents.md:65-82`), the exact status codes, and every retry/backoff detail.
All of it belongs to the carrier adapter spec.

## What the port must never do

- **be named after a carrier.** No carrier-prefixed type, field, event or topic, and no carrier name in a type name
  (`spec.md` D3, `UND-15`, KB `integrations/spedition-pl/README.md:19-36` and its risk R4).
- **expose a carrier endpoint list or payload schema.** The port has four operations; a carrier's thirteen paths and their
  fields are the adapter's business.
- **invent or derive a carrier identity.** It is assigned by a response; a reprint is a render, not a re-registration
  (element 03 RULE-2, element 03 Q-03-1).
- **assume push.** No callback receiver is promised by this port; a webhook, if a future adapter has one, is an optional
  fast path into the same status store (KB `notification-mechanisms.md:20-46`).
- **infer a leg from our own actions beyond L1 and L2.** L3–L6 are carrier-owned facts we cannot compute (element 03
  RULE-4).
- **answer money.** No amount is computed, converted, validated or rounded; COD and declared value pass through (spec.md D5).
- **invent compensation.** Cancelling a booking, voiding a number, replaying a registration after an unknown outcome: none
  is in the sources (`Q-14-2`, `Q-14-3`, `UND-31`, `UND-12`).
- **let a hold cancel a collection.** A hold's effect on delivery is undecided (`UND-22`, element 02 Q-02-4); the port stays
  silent about it.

## Domain rules

- **RULE-1 Registration precedes existence.** Nothing physical is recorded before `register` returns an identity
  `[seg 115-116]`, element 03 RULE-1.
- **RULE-2 Our reference is the only correlation key.** It is unique, ours, stable across retries and independent of the
  carrier identity. `ofTotal` is fixed at registration, because the first label of a split order otherwise says `1/1` and
  is wrong (element 03 RULE-3).
- **RULE-3 Labels are rendered, never negotiated.** A label request never registers, never changes a parcel and never
  consumes a number (element 03, KB `waybill-numbers.md:51-68`).
- **RULE-4 One open collection per parcel.** The request is per parcel, never per multi-parcel handover (element 03
  `markReady`, element 08 RF2): a second `requestCollection` for a parcel that already has an open collection is refused by
  the caller. The rule is ours — no source bounds how many pickups may be booked, so the limit is a decision, not a
  citation.
- **RULE-5 Status is a set union, not a replacement.** Polls may return history or only the latest state; legs are merged
  on `(carrier identity, leg, carrier timestamp)` and never regress (KB `status-polling.md:78-90` C3–C5, element 03
  RULE-5). Publishing only transitions is the port's contract, not a downstream filter.
- **RULE-6 Unknown is a value.** A carrier status we cannot map lands in `unknown` and moves nothing; the raw payload is
  kept so the mapping can be corrected later (`Q-14-1`, KB `status-polling.md:78-90` C4).
- **RULE-7 Legs are ours.** The port's `Leg` enum is L1–L6 with our names; a carrier's codes live and die in the adapter.
- **RULE-8 No money, no arithmetic on the parcel's physical data.** Weight is per parcel and stored as given; who
  measures it and how it is rounded stays open (`UND-06`, element 03 Q-03-2).
- **RULE-9 Credentials are secrets.** Nothing in the port carries a credential; tokens and FIDs are adapter configuration
  (KB `sandbox-and-credentials.md:55-71`).

## State diff

Not applicable — the port is stateless. The state it needs — the two identities, `ofTotal`, the collection handle and the
leg history — lives on `Parcel` (element 03) and in the adapter's own status store (KB `status-polling.md:78-90` C1).

## Domain events

Consumed: `ready for delivery` → `requestCollection` (element 08 calls it). Emitted to the domain, by our caller after the
port returns: `delivery number generated` (`eventstorming.json:128`) at registration, and `delivery state notification`
(`eventstorming.json:133`) per newly stored leg. The port itself emits nothing.

## Q / R carried into this element

- **Q-14-1 open:** the status vocabulary the port standardises on. The first adapter's own status documentation is a public
  archive nobody opened, so its codes are unknown and our L1–L6 names are the operator's reading of `[seg 125]`, not
  carrier codes (KB `status-polling.md:62-76`, `:92-101` U1; `spedition-pl/README.md:164-173` U3). Default: store the raw
  payload, map onto L1–L6, `unknown` otherwise.
- **Q-14-2 open:** what happens after an **unknown outcome** of `register` or `requestCollection` — the carrier documents no
  idempotency key, our reference is unique and never reused, and a repeat is rejected as a duplicate rather than answered
  with the original result (KB `waybill-numbers.md:114-122` U2, U4; `UND-31`). Default: no automatic retry; the case is
  escalated, never silently re-sent with a fresh reference.
- **Q-14-3 open:** whether a hold must cancel an already booked collection (`UND-22` crossing the carrier boundary). No
  source answers it, and the port offers no cancellation because inventing one would decide the question (element 02 Q-02-4).
- **R-14-1 open:** delivery-state latency is our polling interval, not the carrier's, while the business promises a
  notification per leg `[seg 124-126]` (KB `spedition-pl/README.md:98-113`, its risk 1; `status-polling.md:78-90` C7). No
  interval and no quota exists for the first adapter, so the promise has no engineering number behind it.
- **R-14-2 open:** a pickup-point order cannot be expressed carrier-neutrally. The delivery method is content-dependent
  `[seg 79]` and each carrier carries the point in its own field with its own service reclassification, while nobody owns
  the point list or its validation (`UND-16`). The port keeps the point as an opaque value on the request, which is the
  smallest honest shape — not a solved one.
