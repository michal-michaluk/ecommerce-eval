# 05 — GoodsIssueNote

**Role:** **Definition** (immutable printed document, rendered on demand). No Editor exists. Three inputs, and they do
not come from the same place: the *payload* — amounts, invoicing party data, invoice reference — arrives already computed
from the invoicing boundary (`spec.md` D5); the *seller block* is our configuration (`spec.md` F4); the *layout and
captions* are ours to place (KB `goods-issue-note-wz.md:22-49`). Classified **Definition** and not Derived because
nothing is computed in this context at all — the render freezes a payload it received, and a reprint reproduces it
unchanged (RULE-1, RULE-7).

**Definition:** the Polish goods issue note — `Wydanie Zewnętrzne`, numbered `WZ 1/10/2019` — the document recording
that goods left the warehouse, printed with three parties and their NIPs, the lines, the related invoice, a summary
with the amount in words, and two signature slots (artifact `goods-issue-note.json:4`, `:9`, `:10`; KB
`fulfillment/documents/goods-issue-note-wz.md:1`). Who issues it is **open**: the only WZ in the project is a template
preview from a third-party invoicing SaaS (`goods-issue-note.json:5`, `UND-10`). The other branch already has a
mechanical shape — the warehouse port's `IssueNote issue(ReservationId, List<PickedItem>)` returns exactly `IssueNote`,
that is document number, date, lines (`docs/api/warehouse-api.md:37-44`, element 13 `:48`).

## Example instances

One sample exists in the entire project and it is a test preview, not our output. Reproduced field by field, with
artifact line anchors:

```yaml
# the only WZ in the project — FakturaXL.pl template preview (goods-issue-note.json:5)
document:
  title: "Wydanie Zewnętrzne"                          # :9
  number: "WZ 1/10/2019"                               # :10
  issuedAt: { date: "2019-10-05", place: "Katowice" }  # :11
  page: { current: 1, of: 1 }                          # :12
  currency: "PLN"                                      # :13
  language: "pl"                                       # :14
parties:
  seller:                                              # :18-23
    role: "Sprzedawca"
    name: "Firma ABC"
    address: { street: "ul. Szeroka 2", postalCode: "44-555", city: "Katowice", country: "PL" }
    taxId: { type: "NIP", value: "9720902729" }
    email: "firma@fakturaxl.pl"
  buyer:                                               # :25-30
    role: "Nabywca"
    name: "Produkcja Sp. z o.o."
    address: { street: "ul. Stawowa 38", postalCode: "00-967", city: "Warszawa", country: "PL" }
    taxId: { type: "NIP", value: "9372424178" }
  receiver:                                            # :31-36
    role: "Odbiorca"
    name: "Handel s.c."
    address: { street: "ul. Prosta 8", postalCode: "00-121", city: "Warszawa", country: "PL" }
    taxId: { type: "NIP", value: "5111625852" }
lines:                                                 # :39-60
  - position: 1
    name: "Meble ogrodowe"
    quantity: { value: 1, unit: "szt." }
    unitNetPrice: 16200.00
    netValue: 16200.00
    vatRatePercent: 23
    vatValue: 3726.00
    grossValue: 19926.00
  - position: 2
    name: "Dostawa"
    quantity: { value: 1, unit: "szt." }
    unitNetPrice: 10.00
    netValue: 10.00
    vatRatePercent: 23
    vatValue: 2.30
    grossValue: 12.30
relatedDocuments:                                      # :62-64
  - { position: 1, type: "FV", number: "FV 1/09/2019", date: "2019-09-28" }
summary:                                               # :66-73
  netValue: 16210.00
  vatRatePercent: 23
  vatValue: 3728.30
  grossValue: 19938.30
  currency: "PLN"
  amountInWords: "dziewiętnaście tysięcy dziewięćset trzydzieści osiem i 0.30 PLN"
notes: "Dostawa z magazynu głównego. Odbiór po godzinie 15."                        # :75
signatures: { receivedBy: "Jan Kowalski", issuedBy: "Krzysztof Nowak" }             # :77-80
footer:                                                # :82-86
  printSource: "Druk: www.fakturaXL.pl"
  notice: "Stosujemy opakowania recyclingowe!"
  pageLabel: "Strona: 1/1"
```

Second instance — **derived, not captured**. No second WZ exists in the project and no B2C variant is documented
(Q-05-2), so the only honest second instance is the **same note reprinted**: the document side repeats the artifact's
own values unchanged, because RULE-7 forbids re-reading the invoicing boundary. Only our print record differs, and no
source defines it — the shape below is a documented default.

```yaml
# DERIVED, NOT CAPTURED — the reprint of the artifact's note (RULE-7).
# Document side: identical to instance 1, field for field; nothing recomputed, nothing renumbered.
document:
  number: "WZ 1/10/2019"                               # a reprint never renumbers
  issuedAt: { date: "2019-10-05", place: "Katowice" }  # the issue date, not the print date
  page: { current: 1, of: 1 }
summary:
  grossValue: 19938.30                                 # unchanged, never re-derived
  amountInWords: "dziewiętnaście tysięcy dziewięćset trzydzieści osiem i 0.30 PLN"
# Print record — ours, default, defined by no source.
printRecord:
  printOf: "WZ 1/10/2019"
  printSequence: 2                                     # the note is printed twice (KB `:168-171`)
```

Printed caption → field placement is enumerated once in KB `goods-issue-note-wz.md:22`, and the consumer-order shape
stays unknown (`UND-11`, Q-05-2) — nothing here invents one.

### Identity — what identifies what

| Element | Identified by | Evidence |
| --- | --- | --- |
| the note | `document.number`, `WZ 1/10/2019` — prefix, sequence, month, year | artifact `:10`; decomposition is inference (`goods-issue-note-wz.md:51-66`) |
| the invoice | `relatedDocuments[0].number`, `FV 1/09/2019`, plus its `date` | artifact `:63` |
| **our order** | **nothing on the print** — no order id, no processing id, no stock location, no batch | `goods-issue-note-wz.md:213` (W4) |

The WZ identifies the **invoice**, never our order. Order → note correlation is ours to keep in our own store (R-05-1).

### Structure this element must carry

- **three parties, not two**: `Sprzedawca` / `Nabywca` / `Odbiorca`, each with its own NIP; all three NIPs differ in the
  sample and `Odbiorca` sits at a different address than `Nabywca` (artifact `:18-36`, `:89`; KB
  `goods-issue-note-wz.md:68-92`).
- **lines with quantity and unit**, `szt.` even for the `Dostawa` service line (artifact `:43`, `:53`; KB `:94-128`).
- **related documents**: one `FV` reference — the note is not standalone (artifact `:64`, `:92`).
- **summary with amount in words**, the mixed words + `0.30` convention (artifact `:72`; KB `invoice-and-vat-data.md:98`).
- **two signature slots**, `Odebrał` / `Wystawił` — print-time furniture, not a checkbox (artifact `:77-80`, `:93`).
- **document-level free text** `notes`, printed under the `Uwagi` caption (artifact `:75`, `:94`; caption not in the
  transcription — W7).

## Behaviour

- **requested by** — undecided (Q-05-1). Documented default: **rendered on demand, after ready for delivery** —
  `markReady` (element 08) is fixed to exactly two effects and rendering a document is neither of them (08 RULE-1), so the
  WZ is requested by the print action itself, never by the command; the invoicing boundary may supply the payload earlier,
  and in the sample it did — the invoice predates the note by a week (`relatedDocuments[0].date` `2019-09-28` vs
  `issuedAt.date` `2019-10-05`; KB `goods-issue-note-wz.md:129-140`).
- **printed** — twice: `signatures.receivedBy` is signed by the courier/driver on collection and `signatures.issuedBy`
  by the warehouse, which is also why the WZ print job is separate from the consignment-note print job (KB `:168-171`,
  inference — the source names signers, not copies).
- **never** — computes, checks or completes an amount (RULE-1); numbers itself from an invented series (RULE-5);
  identifies the order (RULE-4); is re-rendered from a re-read of the invoicing boundary on reprint (RULE-7); prints the
  template vendor's footer (RULE-8); appears as a CMR, e-CMR, KSeF or returns document (RULE-9).

## Domain rules

- **RULE-1 Amounts are pass-through, never computed.** Every amount and the amount in words — `unitNetPrice`,
  `netValue`, `vatRatePercent`, `vatValue`, `grossValue`, `summary.*`, `amountInWords` — arrives already computed and
  already formatted from the invoicing boundary and is printed verbatim. This context performs no addition,
  subtraction, multiplication, division, percentage, rounding, currency conversion, or reconciliation of an amount
  (`spec.md` D5). Testable: a payload whose line values do not reconcile with `summary` prints unchanged and raises no
  error — the sample itself was reconciled by the KB's arithmetic gate (KB `invoice-and-vat-data.md:73`), never here.
- **RULE-2 No defaulted amount.** A missing amount, `vatRatePercent`, currency or `amountInWords` blocks the render. It
  is never replaced by zero, by a recomputed value, or by digits-spelled-into-words — all three would be computation
  (`spec.md` D5).
- **RULE-3 Three distinct parties.** `seller` is our configuration, not order content (`spec.md` F4; KB
  `ubiquitous-language.md:175-179` leaves the word undefined between two referents); `buyer` is invoicing data;
  `receiver` is a party of its own and is **never** populated from `buyer` or from the delivery address automatically
  (KB `goods-issue-note-wz.md:68-92`). A two-party model is wrong.
- **RULE-4 The note carries no key of this context.** No field on the print may hold an `OrderProcessingId`, a
  `parcelId`, an `orderHash`, a stock location or a batch/serial (KB `goods-issue-note-wz.md:213` W4); correlation to
  the order lives in our store, not on paper.
- **RULE-5 Number shape, not number authority.** The number has the shape `WZ <seq>/<MM>/<YYYY>`, one series per
  document type — the artifact shows sequence `1` in `10` and the related invoice sequence `1` in `09` (inference, KB
  `goods-issue-note-wz.md:51-66`). Reset cadence, uniqueness scope and gaps policy are **undecided** (`UND-11`, Q-05-2), so
  no rule here asserts global uniqueness and no numbering service exists in this context yet.
- **RULE-6 Two signature slots are part of the print.** `signatures.receivedBy` and `.issuedBy` print as named slots
  under `Odebrał` / `Wystawił` (artifact `:77-80`, `:93`). Whether a name is data we hold, a role caption, or a blank
  line for handwriting is open (Q-05-3) — the layout always reserves both.
- **RULE-7 Reprint is identical.** A reprint renders the stored payload; it never re-reads the invoicing boundary and
  never re-derives an amount, so two prints of one number cannot differ (`spec.md` D5, RULE-1). Documented default: the
  payload is stored with the print record.
- **RULE-8 Foreign furniture is dropped.** `footer.printSource` (`Druk: www.fakturaXL.pl`) belongs to the template
  vendor and is never printed (KB `goods-issue-note-wz.md:157-183`).
- **RULE-9 Domestic scope only.** No CMR and no e-CMR for a domestic parcel (KB
  `fulfillment/documents/cmr-and-ecmr.md:97`), no KSeF/e-invoice (`UND-28`), no returns window (KB
  `invoice-and-vat-data.md:139`).

## State diff

Not applicable — the note has no states. It is rendered once per print and RULE-7 keeps every print identical; nothing
about it changes over time, and its only lifecycle fact is the print itself.

## Domain events

None. The board carries no event for a document; printing is a command answered by the renderer. The facts that
*precede* it belong elsewhere: `ready for delivery` / `position packed` on `OrderProcessing` (02), `delivery number
generated` on `Parcel` (03) — the WZ never announces itself as a business state change. It also announces no stock
movement: the warehouse system owns that, the note only records it (KB
`integrations/warehouse-inventree/movements-and-goods-issue.md:140-162`).

## Q / R carried into this element

Numbering is per element (`Q-<element>-n`), following the convention the port and calculation elements already use
(elements 10, 13, 14; `spec.md` D6) — the register is otherwise shared with eleven other files.

- **Q-05-1 open — `UND-10`:** do we issue the WZ, or does the warehouse issue it? Both branches already have a shape:
  our render (this element) or the warehouse port's `issue` returning `IssueNote` — document number, date, lines
  (`docs/api/warehouse-api.md:37-44`, element 13 `:48`, its repeat semantics `:60`). Default here: specified as if we
  render it, because that is the only way to state its fields; in the other branch this element collapses to storing and
  referencing that number (KB `goods-issue-note-wz.md:185-205`).
- **Q-05-2 open — `UND-11`:** numbering (reset cadence, uniqueness scope, gaps policy) and the B2C shape — what fills three
  `NIP` slots when the buyer has none. No blank-party variant exists in any source.
- **Q-05-3 open — W5 / W7:** signature identity (printed name, role, or blank line) and the free-text rules for `notes`,
  whose printed caption `Uwagi` is not in the transcription (KB `goods-issue-note-wz.md:214`, `:216`).
- **Q-05-4 open — W3:** the summary carries a single `vatRatePercent` while VAT lives on the line, so a mixed-rate note has
  no per-rate summary row (KB `goods-issue-note-wz.md:212`). Consequence here: the summary block is a **shape owned by the
  invoicing boundary**, not by us — whichever rows it sends are printed, and this context never restructures them into
  per-rate groups. (W3 is its own gap; `UND-07` covers tax precision and rounding order, which is not this question.)
- **R-05-1 open:** the note has no foreign key to the order (W4, KB `:213`), so reconciliation is only possible through
  our own order → note record. Losing that record makes the paper unattributable.
- **R-05-2 open:** if we do issue it, we take on the numbering series, the three parties including `Odbiorca`, the line VAT,
  the amount in words and the two signatures (KB `goods-issue-note-wz.md:200-201`). The KB names numbering, signature
  identity and the mixed-rate summary as the three fields that make that option non-trivial (`:203-204`) — all three are
  still open here (Q-05-2, Q-05-3, Q-05-4).
