# 01 — Order

**Role:** **Definition** (immutable representation, produced elsewhere). No Editor exists in this context — the order is
never designed or edited here. It is *pinned* by `OrderProcessing` (Process, element 02). Produced by the terminal
`checkout` action; the only thing that crosses the boundary (`eventstorming.json:169-172`,
KB `shopping/checkout-order/order.md:7`).

**Definition:** the immutable document describing what the customer bought and under which terms. It carries content
only — no identity, no status, no history, no money. "From that point order is basically immutable document" `[seg 98]`,
"we basically need to fulfill it" `[seg 106]`.

## Example instances

```yaml
# B2C — pickup point, no NIP
orderHash: "sha256:1f3c9d…"
placedAt: "2026-11-19T09:31:12Z"
lines:
  - productNumber: "75423"
    name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
    quantity: 1
delivery:
  methodCode: "PARCEL_LOCKER"
  location: { kind: PICKUP_POINT, pointId: "KRA01M" }
payment:
  methodCode: "PAY_DEFERRED"
invoicing:
  buyerName: "Jan Kowalski"
  nip: null
  address: { street: "ul. Mineralna 5A", city: "Kraków", postalCode: "30-001", country: PL }
```

```yaml
# B2B — address delivery, NIP, quantity 3
orderHash: "sha256:9ab27e…"
placedAt: "2026-11-19T10:02:44Z"
lines:
  - productNumber: "75423"
    name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
    quantity: 3
delivery:
  methodCode: "COURIER"
  location: { kind: ADDRESS, address: { street: "ul. Mineralna 5", city: "Kraków", postalCode: "30-001", country: PL } }
payment:
  methodCode: "BANK_TRANSFER"
invoicing:
  buyerName: "Firma Testowa Sp. z o.o."
  nip: "6761234567"
  address: { street: "ul. Mineralna 5", city: "Kraków", postalCode: "30-001", country: PL }
```

`methodCode` values are **illustrative only** — no payment or delivery method list exists anywhere (`UND-21`). The
product comes from the one real artifact (`offer-lego.json`, `identifiers.productNumber = 75423`).

## Behaviour

- created only by the order handover (element 12); nothing in this context writes it
- read by `OrderProcessing` (lines → pick list), by the documents (buyer invoicing data, line quantities), never by the
  customer view
- a change to the commitment is a **different order** produced outside the system — the "adjusted order" path
  `[seg 100-105]`, KB `order.md:142`

## Domain rules

- **RULE-1 No identity.** Two orders with equal content are the same order: equality is `orderHash` over the canonical
  content, lines ordered by `productNumber`. No server-assigned field participates.
- **RULE-2 Immutability.** No operation mutates an `Order`; persistence is insert-only. There is no `order amended`
  event on the board (`eventstorming.json:125-135`).
- **RULE-3 Completeness at intake.** Lines, delivery, payment and invoicing must all be present and non-empty, else the
  handover is **rejected** and no processing record is created.
- **RULE-4 Lines.** ≥1 line, integer `quantity ≥ 1`, unique `productNumber` per order.
- **RULE-5 Opaque codes.** `delivery.methodCode` and `payment.methodCode` are non-blank strings. This context never
  enumerates or re-validates them — checkout's guard already did (`UND-21`).
- **RULE-6 No seller.** Seller name/address/NIP is our configuration, not order content (`spec.md` F4,
  `ubiquitous-language.md:175`).
- **RULE-7 One location.** Exactly one of `pointId` / `address`, consistent with `location.kind`.
- **RULE-8 No money.** This context computes, compares and stores no money. An amount appears only as an opaque value
  handed over by its owner: checkout owns the order's prices, the invoicing boundary owns the WZ amounts, the carrier
  request receives the COD / declared-value amount it needs (`spec.md` D5).

## State diff

Not applicable — the order has no states. Every state in the lane belongs to `OrderProcessing` (02).

## Domain events

`Order` emits none. The boundary event `ordered` announces its arrival (element 12).

## Q / R carried into this element

- **Q-01-1 closed (spec.md D4):** synthetic `OrderProcessingId` + `orderHash` dedupe — a second handover of the same order is a no-op.
- **Q-01-2 retired (spec.md D5):** VAT rate and delivery-cost formulas leave this context with the money model.
- **Q-01-3 open:** guest orders (`UND-30`) — `invoicing` may have no customer account behind it.
- **R-01-1 open:** a pickup point id arrives as an opaque string; nothing here validates it, and no source assigns that cache
  or validator (`UND-16`).
