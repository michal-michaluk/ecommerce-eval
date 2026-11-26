# 01 — Customer → Basket view

**Role:** read model + commands. The board's `basket content` read model (`docs/raw/materials/eventstorming.json` `lanes[2].readModels[0]`) with the annotation *"View: - product title, one picture, price"* attached to it — the only view description any material carries. Every action in the basket lane is reachable from this screen.

**Actor:** `Customer` — the board's only actor in the lane (`lanes[2].actors` = `["customer"]`).

**Provenance:** the screen itself exists as one image, `docs/raw/basket.png`, transcribed field by field into `docs/raw/materials/basket.json`; the behaviour behind the screen is `docs/raw/materials/transcript.json` segments 61–79. The screen and the transcript disagree in one direction only: the screen shows **no** delivery estimate, **no** discount and **no** unavailable/out-of-stock marking, all three of which the transcript requires (F9). Where they disagree the mark is drawn from the transcript and flagged `(assumption)` in the prototype.

## Prototype — D-UI1

The UI-prototyping mandate of the specification skill makes a gallery mandatory when a UI is in scope. The repo already carries that pattern (`docs/specs/fulfillment/prototypes/.storybook`), so the basket gallery mirrors it: [`prototypes/.storybook`](prototypes/.storybook), 8 stories, `npm run storybook` on 6012, 6 click-through flows, screenshots in [`prototypes/shots`](prototypes/shots). Gate of record: `npm run verify` (CDP, 14 checks: 8 stories + 6 flows).

| state | story id | what it proves |
|---|---|---|
| the screenshot verbatim | `basket-two-items--default` | every string and number of `basket.json` renders: `164,88` / `9,99` / `174,87` |
| no items | `basket-empty--default` | the empty state (D18) |
| item 2 unchecked | `basket-partial-selection--default` | totals follow the selection, not the basket |
| item 2 out of stock, blocking | `basket-out-of-stock--default` | blocking (D33): the row stays counted, the CTA is disabled with a reason, deselection enables the subset |
| item 2's offer removed | `basket-unavailable--default` | `Niedostępny`, excluded from totals (D18), CTA disabled with a reason |
| estimate + discount | `basket-delivery-estimate--default` | the two things the transcript requires and the screenshot lacks |
| blocking problem | `basket-blocked--default` | a disabled CTA always names its cause |
| after `USUŃ` | `basket-two-items-removed` | flow target for `two items → remove selected → empty` |

## Data shown

| Field | Source | Rule |
|---|---|---|
| page title | `basket.json` `basket.title` | `Koszyk` |
| whole-basket control | `basket.json` `basket.selection.wholeBasket` | checkbox `cały koszyk`, checked by default |
| bulk action | `basket.json` `basket.bulkActions[0]` | `USUŃ`, acts on the selection (D18) |
| shipment header | `basket.json` `basket.shipments[0].label` | `Przesyłka od <seller>`, one group per seller |
| shipment checkbox | `basket.json` `shipments[0].selected` | selects all items of that shipment |
| shipment total | `basket.json` `shipments[0].itemsTotal` | `RAZEM` over the **selected** items of that shipment |
| item title | `basket.json` `items[].title` | full title, wrapping allowed |
| item picture | `basket.json` `items[].image` | one picture per item — the board says one, exactly one |
| item attributes | `basket.json` `items[].attributes[]` | at most the three the capture shows, `label: value` |
| item quantity | `basket.json` `items[].quantity` | stepper, integer, minimum 1 (minus disabled at 1 in the capture) |
| item price | `basket.json` `items[].price` | **line total** for the quantity, not a unit price (D8) |
| item remove | `basket.json` `items[].removeAction` | trash icon, removes that item |
| basket total | `basket.json` `shipments[].itemsTotal` | `RAZEM`, sum of selected item line totals |
| delivery hint | `basket.json` `basket.deliveryHint` | `dostawa od 9,99 zł lub za 0,00 zł ze SMART` |
| summary: product value | `basket.json` `summary.productValue` | `Wartość produktów` over the selection |
| summary: delivery | `basket.json` `summary.delivery` | `Dostawa od`, per seller shipment (D8) |
| summary: total | `basket.json` `summary.totalWithDelivery` | `Razem z dostawą`, emphasised |
| primary action | `basket.json` `summary.primaryAction` | `DOSTAWA I PŁATNOŚĆ` → element 09 |
| secondary action | `basket.json` `summary.secondaryAction` | `KONTYNUUJ ZAKUPY`, a link, not a button |
| cross-sell | `basket.json` `basket.crossSellAction` | `DOBIERZ PRODUKTY ›` |
| tiles | `basket.json` `basket.promotions[]` | donation `Przekaż 10 zł dla Szlachetnej Paczki`, `Allegro Ochrona Kupujących` |

**Formatting:** PLN, two decimals, comma as the decimal separator, non-breaking space before `zł` — `99,89 zł`. Amounts are carried in fine units and rendered at the boundary (D34).

## States

| State | Row mark | Orderable | Primary action |
|---|---|---|---|
| available | none | yes | enabled |
| out of stock | label `Chwilowo niedostępny` **(assumption — no material names it)** | **no — blocking (D33)** | disabled, reason `Odznacz produkty niedostępne, aby przejść dalej` **(assumption)** |
| offer removed | label `Niedostępny` (D18) | no | disabled, same reason (D33) |
| offer data stale (catalog unreachable) | row greyed, `Niedostępny` + `stale` (D26) | no | disabled (D26/D33) |
| blocking problem | the problem is named on the row that causes it | — | disabled, reason names the problem (D18) |

**Rule VIEW-1 — selection is the subject.** Shipment totals, `RAZEM`, `Wartość produktów`, `Razem z dostawą` and every command act on the current selection, never on the whole basket (D18, `basket.json` `selection`).

**Rule VIEW-2 — blocking is a property of the row.** `unavailable` (D18), `outOfStock` (D33) and a failed offer read (D26) all block handover; **deselecting** such a row unblocks checkout for the remaining subset, which is then the only thing counted (VIEW-1).

**Rule VIEW-2b — a disabled action always states why.** No material shows a blocked checkout; the rule exists so the state cannot be silently dropped (transcript 74: "blocking problem blocking the checkout").

**Rule VIEW-3 — one picture, one price, attributes as text.** The board's annotation is the ceiling of what an item row shows; the row carries no description, no stock level, no delivery date of its own.

## Not shown here

- Delivery estimate and discount **lines** exist only in the estimate state — the capture predates them (F9, R7).
- No customer identity in the capture: it has no site header and the transcription records no login state (A1, D11; A2 is superseded by D29).

## Q / R carried into this element

- **Q10 / Q11 / Q12 answered (D18):** empty-basket copy; `Niedostępny` marking and its exclusion from totals; `USUŃ` scope. The prototype marks each as an assumption, not a fact.
- **R7 closed (D33):** an out-of-stock item blocks handover instead of lengthening the estimate, so no "longer estimate" value is needed. `BasketDeliveryEstimate` remains interface-only (D16, issue #71) for the plain estimate.
- **R12** — recorded in `log.md`: only the out-of-stock row label is still a placeholder; the estimate is hardcoded `now + 2 … now + 4` days (D36) and the discount comes flattened from catalog (D22) on the prototype.
