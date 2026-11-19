# 06 — ConsignmentNote

**Role:** **Definition** (immutable printed document, produced by the carrier). No Editor and no renderer of ours: we
build a *request* from our data and store the bytes the carrier prints — "we can only fill the carrier's template", a
generator of our own is a category error (KB `fulfillment/documents/consignment-note.md:93-111`; KB
`domain-language/ubiquitous-language.md:428`).

**Definition:** the carrier's printed carriage document for one parcel — `list przewozowy` at DPD and DHL, `etykieta` in
the same breath, `nalepka` at Poczta Polska: one document, one print (artifact `consignment-note.json:4`; KB
`consignment-note.md:155-184`). It is **not** a CMR and not an e-CMR — for a domestic parcel those never exist (KB
`fulfillment/documents/cmr-and-ecmr.md:97`). Its 45 field paths split 22 carrier / 11 ours / 6 customer / 1 artifact
flag, plus 5 unexercised (KB `consignment-note.md:78-81`).

## Example instances

One captured sample exists — an anonymised DPD test print, reproduced field by field:

```yaml
# the only label in the project — anonymised DPD Polska test print (consignment-note.json:2-5)
carrier:                                               # :7-15  — never authored by us
  brand: "dpd"                                         # :8
  legalEntity: "DPD Polska sp. z o.o."                 # :9
  address: { street: "ul. Mineralna 5", postalCode: "02-274", city: "Warszawa", country: "PL" }   # :10
  website: "www.dpd.com.pl"                            # :11
  branchNumber: "1305"                                 # :12
  routingCode: "N-6102"                                # :13
  gdprNotice: "Państwa dane osobowe są przetwarzane przez DPD Polska sp. z o.o. …"                # :14
sender:                                                # :17-22 — our warehouse/company address
  role: "NADAWCA"                                      # :18
  companyName: "Firma Testowa"                         # :19
  address: { street: "Mineralna 5", postalCode: "PL-02274", city: "Warszawa", country: "PL" }     # :20
  phones: ["+48 801400373", "+48 22 25775555"]         # :21 — ownership disputed, Q-06-2
recipient:                                             # :24-30 — customer, delivery address
  role: "Adres doręczenia"                             # :25
  name: "Fir*** Tes***"                                # :26
  contactPerson: "Ada*** Now***"                       # :27
  anonymized: true                                     # :28
  address: { street: "Mineralna 5", postalCode: "PL-02274", city: "Warszawa", country: "PL", cityPrintedLarge: true }  # :29
parcel:                                                # :32-42
  sequence: 1                                          # :33
  ofTotal: 1                                           # :34
  label: "PACZKA:1/1"                                  # :35
  weight: { value: 1.00, unit: "kg", label: "WAGA: 1.00 kg" }                                     # :36
  dimensions: null                                     # :37
  cod: null                                            # :38
  declaredValue: null                                  # :39
  service: null                                        # :40
  options: []                                          # :41
labelLayout:                                           # :44-47
  orientation: "landscape"                             # :45
  zones:                                               # :46
    - "carrier branding + gdpr notice"
    - "recipient block (large)"
    - "sender block (rotated 90°)"
    - "parcel/weight block"
    - "carrier branch + routing code (rotated 90°)"
    - "empty data area"
```

Second instance:

```yaml
# DERIVED, NOT CAPTURED — no artifact with ofTotal > 1 exists anywhere in the project.
# Shape taken from the KB's own multi-parcel reading (consignment-note.md:113-137), not from a print.
parcel:
  sequence: 2
  ofTotal: 3
  label: "PACZKA:2/3"
  weight: { value: 4.20, unit: "kg", label: "WAGA: 4.20 kg" }
```

Nothing else about a multi-parcel label is known: its weight is per parcel and its `n` is fixed before the first print
(KB `consignment-note.md:113-137`), and everything else stays as in the single sample.

## Behaviour

- **requested by** — `Parcel` (element 03) after `register` returns the carrier's waybill: registration precedes
  existence (`[seg 115-116]`), and the label is a **separate carrier call** from the waybill call (KB
  `integrations/spedition-pl/dpd-polska/labels-and-documents.md:10-19`). Printing is ours; the layout is not.
- **printed** — by the warehouse at packing, on the carrier's own layout — landscape in the only sample (artifact
  `:29`, `:45`; KB `consignment-note.md:93-111`). *When* in the flow, and how a reprint after an outage works, is open
  (`UND-12`, Q-06-1).
- **never** — rendered from our data alone (RULE-2); read back as an input, because it carries no order reference and
  no barcode value (RULE-3); the thing that decides parcel count (RULE-4); where a weight is measured or rounded
  (RULE-5); where any amount is computed or compared (RULE-6); where the carrier's own fields are filled by us
  (RULE-2); a second document beside `etykieta` (RULE-1); a CMR (RULE-1).

## Domain rules

- **RULE-1 One document, many names.** `etykieta` and the parcel's `list przewozowy` are the same single print and must
  never be modelled as two objects; the name is carrier-specific (`nalepka` at Poczta Polska), and none of them is the
  CMR (KB `consignment-note.md:155-184`; KB `cmr-and-ecmr.md:97`).
- **RULE-2 Ownership split is a hard boundary.** Exactly 22 fields come from the carrier, 6 from the customer, 11 from
  us, 1 is an artifact flag. We never author `carrier.*`, the two printed `role` captions, `cityPrintedLarge`, any of
  the six layout `zones`, or the two composed display strings `parcel.label` / `parcel.weight.label` (KB
  `consignment-note.md:24-81`). That the carrier composes those two strings from `sequence`/`ofTotal`/weight is
  **inference** — the KB settles only the owner as "carrier (string form)" (`:61`, `:64`).
- **RULE-3 A printed label is never read back.** The sample carries no order reference, no `parcelNumber`, no
  `trackingNumber` and no barcode/`identcode`, and its `labelLayout.zones[5]` is an explicitly **empty data area**
  (artifact `:54`, `:46`). Correlation is `Parcel.reference` (ours) plus the waybill from the carrier's *response* —
  never from the print (element 03 Q-03-1, `UND-12`).
- **RULE-4 `PACZKA:i/n` is stated, not decided.** `sequence` and `ofTotal` come from `Parcel`; `n` must be fixed before
  the first label, or the first label declares `1/1` and is a false statement once the order is split (artifact
  `:33-35`, `:52`; KB `consignment-note.md:113-137`; element 03 RULE-3).
- **RULE-5 Weight is formatted, never measured or rounded here.** Element 03 holds the weight in **integer grams**
  (`03-parcel.md` RULE-6); the label needs two decimals with an explicit unit, `WAGA: 1.00 kg` (artifact `:36`, `:53`).
  The grams → two-decimal `kg` form belongs to the **label request builder** — the carrier adapter behind the port of
  element 14 — not to the measurement and not to this element; who measures and which rounding mode applies stays open
  (`UND-06`, Q-06-4), so no rounding rule is written here.
- **RULE-6 No money.** `cod` and `declaredValue` are opaque amounts supplied by their owner, printed only when supplied,
  never computed, compared, defaulted or stored here (`spec.md` D5). The sample exercises neither (artifact `:38-39`).
- **RULE-7 Unexercised fields print only when supplied.** `dimensions`, `cod`, `declaredValue`, `service`, `options` are
  `null`/`[]` in the only sample, so their printed form is unknown (`UND-13`, KB `consignment-note.md:192` C3).
  Default: a `null` field prints nothing — nothing is invented to fill the zone.
- **RULE-8 Carrier-agnostic contract.** The request model is the port's contract, not a DPD form: no DPD-only field is
  mandatory, the brand is `carrier.brand` supplied per adapter, and the port is never named after a carrier (`spec.md` D3,
  `UND-15`). The board says `dhl`, the only artifact is DPD — both stay true and neither becomes the model.
- **RULE-9 Reprint reuses the waybill.** A reprint never registers, never changes a parcel and never consumes a number —
  it is a label request against the waybill we already hold, and a carrier outage repair needs that id, not our
  reference (element 03 RULE-1, element 14 RULE-3; KB `consignment-note.md:151-153`). Whether the carrier returns fresh
  bytes or we re-emit stored ones is open (`UND-12`, Q-06-1), and element 03 states the render half while this element
  states the storage half. Default: request again for the same waybill and store what comes back.

## State diff

Not applicable — the printed document is immutable and has no states. The state it reflects — `PACZKA:i/n`, weight,
the five unexercised options — lives on `Parcel` (element 03), and the carrier's own state lives on the carrier side
(element 14).

## Domain events

None. `delivery number generated` and every delivery leg belong to `Parcel` (03); the label print is a command to the
carrier adapter and no board event corresponds to it. Provenance for that absence: every lane, goal, command and event
*name* on the board was searched for `print`, `etykiet`, `dokument`, `document` and `WZ` and for `label` outside its
structural `"label":` key — 0 hits; the 7 `label` occurrences are that key naming lanes and goals
(`eventstorming.json:14-15, 21, 57, 73, 89, 111`).

## Field ownership table

Per field path, owner and evidence — the KB's field table verbatim in ownership order (KB `consignment-note.md:24-92`);
an artifact anchor is given wherever the field has one of its own, and the five `zones` share the array at
`consignment-note.json:46`.

**Carrier — 22 fields we never author**

| Field | Sample | Evidence |
| --- | --- | --- |
| `carrier.brand` | `dpd` | KB `:32`, artifact `:8` |
| `carrier.legalEntity` | `DPD Polska sp. z o.o.` | KB `:33`, artifact `:9` |
| `carrier.address.street` | `ul. Mineralna 5` (image prints `5A`) | KB `:34`, artifact `:10` |
| `carrier.address.postalCode` | `02-274` | KB `:35`, artifact `:10` |
| `carrier.address.city` | `Warszawa` | KB `:36`, artifact `:10` |
| `carrier.address.country` | `PL` (inference — not a separate token on the image) | KB `:37`, artifact `:10` |
| `carrier.website` | `www.dpd.com.pl` | KB `:38`, artifact `:11` |
| `carrier.branchNumber` | `1305` | KB `:39`, artifact `:12` |
| `carrier.routingCode` | `N-6102` | KB `:40`, artifact `:13` |
| `carrier.gdprNotice` | the DPD GDPR sentence | KB `:41`, artifact `:14` |
| `sender.role` | `NADAWCA` (fixed caption) | KB `:42`, artifact `:18` |
| `recipient.role` | `Adres doręczenia` — not `Odbiorca` | KB `:50`, artifact `:25` |
| `recipient.address.cityPrintedLarge` | `true` (layout rule) | KB `:58`, artifact `:29` |
| `parcel.label` | `PACZKA:1/1` (composed string) | KB `:61`, artifact `:35` |
| `parcel.weight.label` | `WAGA: 1.00 kg` (composed string) | KB `:64`, artifact `:36` |
| `labelLayout.orientation` | `landscape` | KB `:70`, artifact `:45` |
| `labelLayout.zones[0]` | carrier branding + gdpr notice | KB `:71`, artifact `:46` |
| `labelLayout.zones[1]` | recipient block (large) | KB `:72`, artifact `:46` |
| `labelLayout.zones[2]` | sender block (rotated 90°) | KB `:73`, artifact `:46` |
| `labelLayout.zones[3]` | parcel/weight block | KB `:74`, artifact `:46` |
| `labelLayout.zones[4]` | branch + routing code (rotated 90°) | KB `:75`, artifact `:46` |
| `labelLayout.zones[5]` | empty data area — where a barcode would sit | KB `:76`, artifact `:46` |

**Ours — 11 fields**

| Field | Sample | Evidence |
| --- | --- | --- |
| `sender.companyName` | `Firma Testowa` | KB `:43`, artifact `:19` |
| `sender.address.street` | `Mineralna 5` | KB `:44`, artifact `:20` |
| `sender.address.postalCode` | `PL-02274` (our value, carrier's `PL-` form) | KB `:45`, artifact `:20` |
| `sender.address.city` | `Warszawa` | KB `:46`, artifact `:20` |
| `sender.address.country` | `PL` (inference) | KB `:47`, artifact `:20` |
| `sender.phones[0]` | `+48 801400373` — **disputed** | KB `:48`, artifact `:21` |
| `sender.phones[1]` | `+48 22 25775555` — **disputed** | KB `:49`, artifact `:21` |
| `parcel.sequence` | `1` | KB `:59`, artifact `:33` |
| `parcel.ofTotal` | `1` | KB `:60`, artifact `:34` |
| `parcel.weight.value` | `1.00` (measured — by whom is unstated) | KB `:62`, artifact `:36` |
| `parcel.weight.unit` | `kg` | KB `:63`, artifact `:36` |

**Customer — 6 fields**

| Field | Sample | Evidence |
| --- | --- | --- |
| `recipient.name` | `Fir*** Tes***` | KB `:51`, artifact `:26` |
| `recipient.contactPerson` | `Ada*** Now***` | KB `:52`, artifact `:27` |
| `recipient.address.street` | `Mineralna 5` | KB `:54`, artifact `:29` |
| `recipient.address.postalCode` | `PL-02274` (image: large bold) | KB `:55`, artifact `:29` |
| `recipient.address.city` | `Warszawa` (largest text on the label) | KB `:56`, artifact `:29` |
| `recipient.address.country` | `PL` (inference) | KB `:57`, artifact `:29` |

**Flag — 1**

| Field | Sample | Evidence |
| --- | --- | --- |
| `recipient.anonymized` | `true` — artifact-only, never printed | KB `:53`, artifact `:28` |

**Unexercised — 5, owner "ours when used", outside the 22/11/6/1 tally**

| Field | Sample | Evidence |
| --- | --- | --- |
| `parcel.dimensions` | `null` | KB `:65`, artifact `:37` |
| `parcel.cod` | `null` — amount opaque, `spec.md` D5 | KB `:66`, artifact `:38` |
| `parcel.declaredValue` | `null` — amount opaque, `spec.md` D5 | KB `:67`, artifact `:39` |
| `parcel.service` | `null` | KB `:68`, artifact `:40` |
| `parcel.options` | `[]` | KB `:69`, artifact `:41` |

Tally: 45 field paths; 40 populated = 22 carrier + 11 ours + 6 customer + 1 flag (KB `consignment-note.md:78-81`). The
split counts `sender.phones` as ours, per the JSON, although the image prints those numbers in the rotated carrier block
— Q-06-2.

## Q / R carried into this element

Numbering is per element (`Q-<element>-n`), following the convention the port and calculation elements already use
(elements 10, 13, 14; `spec.md` D6) — the register is otherwise shared with eleven other files.

- **Q-06-1 open — `UND-12`:** the provider-id lifecycle — when the label is requested and printed, whether a reprint after
  a carrier outage returns fresh bytes or re-emits stored ones (RULE-9's default is the first), and the barcode symbology
  and payload (KB `consignment-note.md:190` C1, `:191` C2; KB
  `integrations/spedition-pl/dpd-polska/waybill-numbers.md:69-81`).
- **Q-06-2 open — `UND-13`:** `sender.phones` ownership (JSON says sender, image prints them in the carrier block with the
  sender's `Tel.:` blank), the carrier street `5` vs `5A`, and the printed form of the five unexercised fields (KB
  `consignment-note.md:83-92`, `:192` C3, `:194` C5).
- **Q-06-3 open — C6:** who fills `recipient.contactPerson` — customer-provided or carrier-required (KB
  `consignment-note.md:195`).
- **Q-06-4 open — `UND-06`, element 03 Q-03-2 restated at print:** weight source (measured at packing vs declared per
  product), the rounding mode, and — the part this element owns — who converts the grams element 03 holds into the
  label's two-decimal `kg` form (RULE-5 names the label request builder as the default owner; the sources name nobody,
  KB `consignment-note.md:193` C4).
- **Q-06-5 open — `UND-15` and `UND-16` carried:** the board's carrier is `dhl` while the only artifact is DPD, so the
  brand stays data (`consignment-note.json:8`; KB `integrations/spedition-pl/README.md:19-38`); and a pickup point
  travels only as an opaque value on the request, with nobody owning the list, its validation or the carrier-service
  choice it implies (element 14 `R-14-2`; KB `integrations/spedition-pl/carriers/pickup-points.md:101-110`).
- **R-06-1 open:** copying the transcription literally prints DPD's own hotline as our sender phone (C5, KB
  `consignment-note.md:194`) — a live defect on a printed label, not a cosmetic one.
- **R-06-2 open:** a label printed before `ofTotal` is decided declares `1/1` and misstates the shipment (KB
  `consignment-note.md:133`; element 03 RULE-3).
- **R-06-3 open:** no statute in the evidence set imposes a domestic consignment-note duty, so "the label is legally
  required" cannot be asserted here (KB `consignment-note.md:196` C7; KB `cmr-and-ecmr.md:113` M2).
