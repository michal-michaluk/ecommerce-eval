# KB index — the fulfillment input set

Generated from `/Users/michal/.agents/knowledge/ecommerce/` (KB repo `/Users/michal/.agents`, RAG collection `ecommerce`).

Link-only: this file indexes the knowledge base, it does not copy it. Line numbers are `## ` headings, so a claim can be cited as `KB <file>:<line>`.

| folder | files | lines | coverage (KB `index.md:146-147`) |
| --- | --- | --- | --- |
| `fulfillment/order-processing/` | 8 | 1003 | `[partial]` assignment, pick, pack, delivery chain, issues — picklist algorithm missing |
| `fulfillment/documents/` | 7 | 1052 | `[done]` WZ, consignment note, CMR/e-CMR, invoice data |
| `integrations/warehouse-inventree/` | 8 | 1269 | `[done]` identity, allocation, movements, hooks, port constraints |
| `integrations/spedition-pl/` | 20 | 2578 | `[done]` matrix, DPD reference, five other carriers, notification analysis, pickup points |
| `domain-language/` | 3 | 898 | `[done]` ubiquitous language, glossary, undecided register |

## `fulfillment/order-processing/`

### `fulfillment/order-processing/README.md` — 134 lines
- `:7` What this area is responsible for
- `:20` Actors
- `:32` Commands (from the board)
- `:49` Events (from the board)
- `:65` Read models (from the board)
- `:79` Policy
- `:87` Boundary: what enters
- `:97` Boundary: what leaves
- `:109` Where this area stops
- `:116` Contents
- `:128` Sources

### `fulfillment/order-processing/issues-and-hold.md` — 142 lines
- `:5` What a problem is
- `:23` Issue types — as the sources name them
- `:36` Hold
- `:51` The issue dashboard and who resolves
- `:62` When the issue cannot be resolved on the spot
- `:86` State diagram — hold, resolution, adjustment
- `:112` Undecided / not in sources
- `:128` Sources

### `fulfillment/order-processing/order-assignment.md` — 111 lines
- `:5` The guard
- `:18` Orders list — the states the sources name
- `:30` Order state machine
- `:50` Provenance of every state and transition
- `:74` What assignment triggers
- `:83` Why the assignment must be exclusive rather than advisory
- `:87` Undecided / not in sources
- `:99` Sources

### `fulfillment/order-processing/pick-list.md` — 146 lines
- `:5` What the list contains
- `:19` The optimisation gap
- `:32` What a resolver must ask next
- `:44` How the list and the reservation relate
- `:60` `reserve products` — short-term reservation
- `:79` Sync / async question
- `:93` `stock changed` — inbound notifications
- `:104` The constraint behind `stock changed`
- `:116` Undecided / not in sources
- `:130` Sources

### `fulfillment/order-processing/picking-and-packing.md` — 96 lines
- `:11` The workstation
- `:22` Position-level state
- `:46` The contradiction you must resolve
- `:58` What the scan is a confirmation of
- `:69` Undecided / not in sources
- `:84` Sources

### `fulfillment/order-processing/research-log.md` — 101 lines
- `:6` What the sources give
- `:17` Coverage against the assigned scope
- `:29` Broken or unreadable resources
- `:39` Inheritance instead of verification
- `:51` Rejected source
- `:55` Decisions taken here that the operator must confirm
- `:65` Gate results
- `:81` Known holes carried forward
- `:92` Sources

### `fulfillment/order-processing/shipping-and-delivery-states.md` — 146 lines
- `:5` Parcel registration — at the very beginning
- `:23` Which provider
- `:34` Ready for delivery — two effects in one command
- `:48` The delivery chain
- `:65` State machine — parcel and order together
- `:92` The reality constraint: push cannot cover every leg
- `:114` Undecided / not in sources
- `:127` Sources

### `fulfillment/order-processing/sources-and-references.md` — 127 lines
- `:3` Repository base
- `:11` Source tiers
- `:24` Citation conventions used in this area
- `:34` Segment index for this area
- `:59` Board index for this area
- `:73` Disagreements with the superseded doc
- `:99` Web sources
- `:109` Unverified in this area
- `:119` Sources

## `fulfillment/documents/`

### `fulfillment/documents/README.md` — 106 lines
- `:6` Tier legend
- `:16` Document landscape
- `:37` Legal-required vs optional
- `:53` Cross-links
- `:58` Table of contents
- `:89` Undecided / not in sources
- `:100` Sources

### `fulfillment/documents/cmr-and-ecmr.md` — 125 lines
- `:8` What CMR is
- `:40` Why it does not apply to a domestic parcel
- `:53` What e-CMR changed
- `:71` Polish status of a *domestic* electronic consignment note
- `:85` Practical consequence for a domestic shop
- `:97` Decision table
- `:108` Undecided / not in sources
- `:118` Sources

### `fulfillment/documents/consignment-note.md` — 204 lines
- `:9` Evidence base
- `:24` Field table — every field, its owner, its source
- `:83` Transcription note (image vs JSON)
- `:93` What we actually control
- `:113` `PACZKA:1/1` — multi-parcel semantics
- `:138` The JSON's own open question: no parcel number, no barcode
- `:155` Terminology finding — `list przewozowy` vs `etykieta` vs `CMR`
- `:186` Undecided / not in sources
- `:198` Sources

### `fulfillment/documents/goods-issue-note-wz.md` — 224 lines
- `:7` Evidence base
- `:22` Printed captions → field mapping
- `:51` Document identity
- `:68` The three-party pattern
- `:94` Lines
- `:129` Related documents
- `:142` Summary and amount in words
- `:157` Free text, signatures, footer furniture
- `:185` Ownership decision — open
- `:206` Undecided / not in sources
- `:219` Sources

### `fulfillment/documents/invoice-and-vat-data.md` — 173 lines
- `:9` Invoicing is data, not a document
- `:42` The seller-side promise
- `:58` NIP on the WZ
- `:73` VAT rates and the arithmetic
- `:98` Amount in words
- `:116` What this means for the document layer
- `:131` KSeF / e-invoicing — OUT of scope
- `:139` The return window — a customer-facing date, not a document
- `:155` Undecided / not in sources
- `:166` Sources

### `fulfillment/documents/research-log.md` — 141 lines
- `:5` Inputs read in full
- `:19` T1 — transcript term scan (the absence finding)
- `:46` T2 — arithmetic gate
- `:63` T3 — external source verification
- `:76` Failures and limitations
- `:87` Decisions taken while writing
- `:102` Review corrections
- `:124` Quality gates run
- `:137` Sources

### `fulfillment/documents/sources-and-references.md` — 79 lines
- `:5` Artifact sources (local, authority for document shape)
- `:19` Law sources (external, authority for legal scope)
- `:30` Carrier terminology sources (external, authority for what each carrier calls the print)
- `:38` Reference points used across this area
- `:51` Underlying images (present, verified)
- `:63` What is deliberately absent
- `:74` Sources

## `integrations/warehouse-inventree/`

### `integrations/warehouse-inventree/README.md` — 113 lines
- `:10` What InvenTree is, in verified facts
- `:33` The rule: we do not run it, we translate to it
- `:46` What the business material actually asks the warehouse for
- `:64` Capability table — what fulfillment needs vs what InvenTree offers
- `:88` Files
- `:100` Sources

### `integrations/warehouse-inventree/availability-and-allocation.md` — 257 lines
- `:6` Four quantities, one identity
- `:46` Status gates availability
- `:80` What `reserve` really is: Allocation
- `:105` Which carrier to allocate against
- `:122` `stock count` is not an availability read
- `:129` No TTL — allocations live until someone ends them
- `:144` Reserve → pick → issue, with the seams marked
- `:188` The three pieces of state that are ours
- `:205` Why "move stock to a hold location" is not a reservation
- `:228` Undecided / not in sources
- `:246` Sources

### `integrations/warehouse-inventree/identity-and-api.md` — 159 lines
- `:8` Identity
- `:22` The vendored schema is two API versions behind — and verified equivalent for our paths
- `:47` Stack, as read from the source (not from marketing)
- `:56` Where the schema is served
- `:71` Authentication
- `:104` The pagination quirk: `limit` is required in the schema, optional at runtime
- `:121` Endpoint families we care about
- `:138` Undecided / not in sources
- `:150` Sources

### `integrations/warehouse-inventree/integration-hooks-and-picking.md` — 155 lines
- `:7` The finding: no outbound subscription exists
- `:20` `EventMixin`, quoted precisely
- `:43` Barcode endpoints — the picking surface
- `:69` What this forces on our design
- `:91` The `inventree-shopify` precedent — ecosystem evidence, not authority
- `:108` What transfers to us
- `:117` What does not transfer
- `:132` Undecided / not in sources
- `:146` Sources

### `integrations/warehouse-inventree/movements-and-goods-issue.md` — 209 lines
- `:8` The verbs
- `:42` `count` is a correction, not a read — and the business material needs it
- `:50` Payload shapes
- `:90` Goods issue: three different things wear that name
- `:99` Why G3 is not a goods issue
- `:118` Transfer order: the other levers
- `:140` Where the goods issue note (WZ) fits
- `:163` Worked end-to-end shape (schema-derived)
- `:181` Undecided / not in sources
- `:197` Sources

### `integrations/warehouse-inventree/port-constraints.md` — 187 lines
- `:7` The port, verbatim
- `:35` M1 `stockAt(ProductId)` → `List<StockAtLocation>`
- `:47` M2 `reserve(ProductId, LocationCode, int)` → `ReservationId`
- `:63` M3 `release(ReservationId)` → void
- `:72` M4 `issue(ReservationId, List<PickedItem>)` → `IssueNote`
- `:85` Decision table
- `:98` The state the adapter must own — as a model
- `:150` Where the local consumer doc is wrong or imprecise
- `:163` Undecided / not in sources
- `:177` Sources

### `integrations/warehouse-inventree/research-log.md` — 122 lines
- `:6` Method, in order
- `:28` Corrections to the brief
- `:39` Discoveries not in the brief
- `:53` The one differential verification
- `:66` Verification status of the load-bearing claims
- `:80` Review round — corrections applied after independent review
- `:97` What remains unverified
- `:114` Sources

### `integrations/warehouse-inventree/sources-and-references.md` — 67 lines
- `:6` Local files
- `:21` Remote sources, fetched
- `:46` Sources that did not work, and what was done instead
- `:55` What was deliberately not used
- `:64` Sources

## `integrations/spedition-pl/`

### `integrations/spedition-pl/README.md` — 207 lines
- `:10` Why DPD Polska is the reference
- `:19` The contradiction: the board says `dhl`
- `:38` What a carrier must do for us
- `:47` Carrier comparison matrix
- `:65` Endpoint-level provenance for the matrix
- `:98` Consequence: push-per-leg is required, polling is what we get
- `:115` Contents
- `:117` `dpd-polska/`
- `:131` `carriers/` — the other six carriers
- `:155` Risks
- `:164` Undecided / not in sources
- `:175` Outside knowledge
- `:182` Sources

### `integrations/spedition-pl/dpd-polska/README.md` — 111 lines
- `:12` The four interactions, in flow order
- `:24` Sequence
- `:59` What our parcel record must hold
- `:73` Contents
- `:91` Undecided / not in sources
- `:100` Sources

### `integrations/spedition-pl/dpd-polska/api-surface.md` — 160 lines
- `:11` API modules
- `:32` Documentation access
- `:46` Authentication and account identity
- `:61` Endpoint inventory (complete, 13 paths / 15 operations)
- `:87` Capability statement from the spec itself
- `:96` Error-handling policy
- `:112` Confirmed negatives
- `:125` What is published but unopened
- `:135` Undecided / not in sources
- `:145` Outside knowledge
- `:151` Sources

### `integrations/spedition-pl/dpd-polska/labels-and-documents.md` — 135 lines
- `:10` Two documents, two calls
- `:19` Label call, field by field
- `:38` Protocol call, field by field
- `:53` What the sample label contains
- `:65` Ownership split — the rule the adapter must encode
- `:84` Open questions the artifact itself raises
- `:96` CMR and e-CMR are out of scope here
- `:105` Undecided / not in sources
- `:115` Outside knowledge
- `:121` Sources

### `integrations/spedition-pl/dpd-polska/research-log.md` — 134 lines
- `:6` Method
- `:19` Local material actually read
- `:31` Live checks
- `:48` Corrections to the research brief
- `:65` Rejected claims
- `:77` Sibling areas written concurrently
- `:91` Unverified — carried forward
- `:106` Undecided / not in sources
- `:113` What the operator still has to decide
- `:122` Sources

### `integrations/spedition-pl/dpd-polska/sandbox-and-credentials.md` — 101 lines
- `:6` Environments
- `:19` Published demo credentials
- `:32` What can be exercised in demo
- `:46` Production credentials
- `:55` How the shop must store them
- `:72` Undecided / not in sources
- `:83` Outside knowledge
- `:89` Sources

### `integrations/spedition-pl/dpd-polska/services-and-options.md` — 155 lines
- `:7` The four that our business data implies
- `:9` `COD` — cash on delivery (pobranie)
- `:26` `DECLARED_VALUE` — shipment insurance
- `:41` `DPD_PICKUP` with the `PUDO` attribute — delivery to a pickup point
- `:63` Multi-parcel senders
- `:75` Complete service list from the spec
- `:112` Exclusion rules the adapter must enforce before calling
- `:128` Undecided / not in sources
- `:139` Outside knowledge
- `:145` Sources

### `integrations/spedition-pl/dpd-polska/sources-and-references.md` — 96 lines
- `:6` Local business sources
- `:16` Web sources
- `:35` Derived artifacts
- `:42` Contradictions and how they were handled
- `:54` Cross-links
- `:69` Reading order for an adapter developer
- `:79` Undecided / not in sources
- `:87` Sources

### `integrations/spedition-pl/dpd-polska/status-polling.md` — 121 lines
- `:8` What exists, publicly
- `:22` What "delivery state notification" therefore becomes
- `:34` The chain the business named
- `:62` Business status → carrier source → confidence
- `:78` Consequences for the implementation
- `:92` Undecided / not in sources
- `:103` Outside knowledge
- `:109` Sources

### `integrations/spedition-pl/dpd-polska/waybill-numbers.md` — 134 lines
- `:10` Board vocabulary this implements
- `:23` Request shape
- `:51` Response shape
- `:69` Two identities, never merged
- `:81` One package, N parcels
- `:94` Worked example — our sample label, field by field
- `:114` Undecided / not in sources
- `:124` Sources

### `integrations/spedition-pl/carriers/README.md` — 84 lines
- `:13` Scope
- `:26` The carriers and what each one costs us
- `:47` Table of contents
- `:59` How to choose a second carrier from this folder
- `:68` Undecided / not in sources
- `:78` Sources

### `integrations/spedition-pl/carriers/dhl-parcel-dhl24.md` — 161 lines
- `:6` Contract
- `:22` Auth and key provisioning
- `:32` Operations actually published in the WSDL
- `:44` Shipment creation and IDs
- `:58` Labels
- `:73` Status retrieval — polling only
- `:84` Collection request and service points
- `:98` Sandbox
- `:115` The contradiction: board says DHL, artifact says DPD
- `:133` Undecided / not in sources
- `:145` Sources

### `integrations/spedition-pl/carriers/gls-poland.md` — 120 lines
- `:8` Contract
- `:23` Session lifecycle — the constraint that shapes the client
- `:40` Operations published on the manual's news page
- `:67` REST tracking (the alternative to SOAP for T&T)
- `:83` What an integration team must ask GLS for before writing code
- `:102` Undecided / not in sources
- `:112` Sources

### `integrations/spedition-pl/carriers/inpost-shipx.md` — 130 lines
- `:6` API shape and hosts
- `:21` Labels
- `:44` Points / Paczkomat as a delivery method
- `:63` Webhooks — the only push in the set
- `:79` Collection request
- `:86` Mapping onto our flow
- `:96` Sandbox gaps
- `:107` Undecided / not in sources
- `:118` Sources

### `integrations/spedition-pl/carriers/notification-mechanisms.md` — 147 lines
- `:8` Carrier matrix
- `:20` Verdict
- `:80` Idempotency — the consequence nobody escapes
- `:99` Mapping the requirement onto legs
- `:118` Undecided / not in sources
- `:131` Sources

### `integrations/spedition-pl/carriers/pickup-points.md` — 136 lines
- `:9` InPost — `parcel_locker` vs `pop`
- `:29` The size/weight constraint, in numbers
- `:50` DPD Polska — point services
- `:59` DHL Parcel — service points
- `:68` GLS Poland — parcel shops
- `:76` Poczta Polska — post offices, partner networks, APM, PUDO
- `:90` What this costs us across carriers
- `:99` Open questions
- `:116` Undecided / not in sources
- `:124` Sources

### `integrations/spedition-pl/carriers/poczta-polska-e-nadawca.md` — 156 lines
- `:7` Contract and environments
- `:24` `addShipment` semantics
- `:46` `sendEnvelope`
- `:61` Printing the "nalepka"
- `:82` Status retrieval — envelope-level, and that is the ceiling
- `:100` Password rotation as an operational constraint
- `:117` Delivery channel and pickup points
- `:129` "Pobranie" (COD), as the manual defines it
- `:138` Undecided / not in sources
- `:149` Sources

### `integrations/spedition-pl/carriers/research-log.md` — 91 lines
- `:3` Method
- `:17` What was fetched successfully
- `:29` Failures and blocks
- `:42` Corrections to the briefing this folder started from
- `:53` Corrections made during the run
- `:67` Judgements made while writing
- `:77` Open questions carried forward
- `:87` Sources

### `integrations/spedition-pl/carriers/sources-and-references.md` — 117 lines
- `:11` InPost ShipX
- `:33` DHL Parcel — DHL24 WebAPI
- `:53` GLS Poland — ADE-Plus WebAPI 2
- `:68` Poczta Polska — e-Nadawca
- `:80` UPS
- `:92` FedEx
- `:102` Our business sources
- `:114` Sources

### `integrations/spedition-pl/carriers/ups-and-fedex.md` — 82 lines
- `:7` What they add over the Polish carriers
- `:20` UPS — verified
- `:39` FedEx — verified, and deliberately partial
- `:56` Why lower priority
- `:65` Undecided / not in sources
- `:72` Sources

## `domain-language/`

### `domain-language/glossar.md` — 106 lines
- `:6` a. Term count by group and by status
- `:21` b. The 15 most load-bearing terms
- `:41` c. Entries that are not `confirmed`
- `:43` `missing` — the concept exists, no source defines it (8)
- `:56` `ambiguous` — two sources mean different things by one word (13)
- `:74` d. This is a Polish-language business described in English
- `:101` Sources

### `domain-language/ubiquitous-language.md` — 687 lines
- `:10` How the status was decided
- `:32` 1. Offer and catalog
- `:34` offer (pl: oferta)
- `:41` product (pl: produkt)
- `:48` product version (pl: wersja produktu)
- `:55` draft (pl: szkic)
- `:62` product description (pl: opis produktu)
- `:69` quality policy (pl: polityka jakości)
- `:76` review (pl: przegląd)
- `:83` publication (pl: publikacja)
- `:90` availability date (pl: data dostępności)
- `:97` price (pl: cena)
- `:104` discount (pl: rabat / zniżka)
- `:111` price range (pl: zakres cenowy)
- `:118` active price (pl: cena aktywna)
- `:125` removal (pl: usunięcie z oferty)
- `:132` revert to previous version (pl: przywrócenie poprzedniej wersji)
- `:139` frozen version / snapshot (pl: zamrożona wersja / migawka)
- `:146` variant (pl: wariant)
- `:153` parameter (pl: parametr)
- `:160` media (pl: media / zdjęcia)
- `:167` offer creation process (pl: proces tworzenia oferty)
- `:174` seller (pl: sprzedawca)
- `:183` 2. Shopping
- `:185` customer (pl: klient)
- `:192` basket (pl: koszyk)
- `:199` koszyk (pl: koszyk)
- `:206` basket item (pl: pozycja koszyka)
- `:213` out of stock (pl: brak w magazynie / towar niedostępny)
- `:220` non-blocking issue (pl: problem nieblokujący)
- `:227` blocking issue (pl: problem blokujący)
- `:234` delivery method (pl: metoda dostawy)
- `:241` payment method (pl: metoda płatności)
- `:248` delivery time (pl: czas dostawy)
- `:255` delivery cost (pl: koszt dostawy)
- `:262` discount on basket (pl: rabat na koszyk)
- `:269` checkout (pl: zamówienie / kasa)
- `:276` guard (pl: zabezpieczenie / blokada)
- `:283` invoicing data (pl: dane do faktury)
- `:290` order (pl: zamówienie)
- `:297` immutability (pl: niezmienność)
- `:306` 3. Fulfillment
- `:308` order processing (pl: realizacja zamówienia)
- `:315` assignment (pl: przypisanie pracownika)
- `:322` pick list (pl: lista kompletacyjna)
- `:329` pick (pl: pobranie / kompletacja)
- `:336` pack (pl: pakowanie)
- `:343` scan (pl: skanowanie)
- `:350` position packed (pl: pozycja spakowana)
- `:357` ready for delivery (pl: gotowe do wysyłki)
- `:364` delivery number / provider ID / numer przesyłki (pl: numer przesyłki)
- `:370` delivery state (pl: stan dostawy)
- `:377` delivery state notification (pl: powiadomienie o stanie dostawy)
- `:384` issue (pl: zgłoszenie problemu)
- `:391` order on hold (pl: zamówienie wstrzymane)
- `:398` adjusted order (pl: zamówienie skorygowane)
- `:407` 4. Documents
- `:409` WZ / wydanie zewnętrzne (pl: wydanie zewnętrzne)
- `:416` list przewozowy (pl: list przewozowy)
- `:423` etykieta / label (pl: etykieta)
- `:429` CMR (pl: list przewozowy CMR)
- `:436` e-CMR (pl: elektroniczny list przewozowy)
- `:443` nalepka (pl: nalepka)
- `:450` paczka i/n (pl: paczka i z n)
- `:457` pobranie / COD (pl: pobranie)
- `:464` wartość deklarowana (pl: wartość deklarowana)
- `:471` faktura (pl: faktura)
- `:478` NIP (pl: NIP)
- `:485` amount in words (pl: słownie)
- `:494` 5. Integration
- `:496` warehouse (pl: magazyn)
- `:503` magazyn (pl: magazyn)
- `:510` stock (pl: stan magazynowy)
- `:517` location (pl: lokalizacja)
- `:524` reservation (pl: rezerwacja)
- `:531` allocation (pl: alokacja)
- `:544` customer master (pl: źródło prawdy o kliencie)
- `:551` CRM (pl: CRM)
- `:558` sales order (pl: zamówienie sprzedaży)
- `:565` webhook (pl: webhook)
- `:572` polling (pl: odpytywanie)
- `:579` sandbox (pl: środowisko testowe)
- `:586` master FID (pl: numer klienta przewoźnika)
- `:595` 6. Spedition
- `:597` spedition (pl: spedycja)
- `:604` carrier (pl: przewoźnik)
- `:611` courier (pl: kurier)
- `:618` pickup point (pl: punkt odbioru)
- `:625` paczkomat (pl: paczkomat)
- `:632` parcel (pl: paczka)
- `:639` consignment (pl: przesyłka)
- `:646` handover protocol (pl: protokół przekazania)
- `:655` Polish spellings the sources never use
- `:674` Sources

### `domain-language/undecided-register.md` — 105 lines
- `:17` A. Money and time
- `:32` B. Documents and identity
- `:44` C. Ownership and integrations
- `:58` D. Process gaps
- `:69` E. Discovered, not researched
- `:89` What this register is not
- `:96` Sources

