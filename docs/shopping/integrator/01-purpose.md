# Part I — Calling the API

## 1 Purpose and scope

This guide is for the developer who has to call the shopping service. It states what each route accepts,
what it answers, what it refuses and what a client must do about a refusal. It does not explain how the
service is built — that is the system documentation — and it does not describe an order, because this
service creates none.

<!-- evidence: docs/specs/basket/index.md:7-8, services/shopping/src/main/java/shop/shopping/checkout/CheckoutService.java:20-21 -->

### 1.1 The shape of the interface

Six routes, one resource hierarchy, no pagination and no batch endpoint:

| Route | What it does |
|---|---|
| `GET /baskets` | the caller's active baskets, creating the one they always own |
| `GET /baskets/{basketId}` | one basket: content, totals, delivery estimate |
| `PATCH /baskets/{basketId}/items/{offerId}` | set one row's absolute quantity, or add the offer |
| `PATCH /baskets/{basketId}/items` | remove one row, the selection, or everything |
| `PATCH /baskets/{basketId}/selection` | tick or untick one row, one seller's shipment, or all |
| `PATCH /baskets/{basketId}/checkout` | hand the selection over |

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:60-74 -->

Three decisions shape everything a client does with it:

- **Reads are `GET`, every update is `PATCH`.** A command that states an absolute quantity is idempotent
  by construction, so a retried `PATCH …/items/OF-1001` cannot double a basket the way a relative change
  would.
- **Every answering route re-reads the offers.** A command's answer is the basket as the catalog describes
  it now, not as it was when the row was added — so a client never has to re-read after a command.
- **There is one error body.** Every refusal carries an `error` code and the values it refuses, never a
  message; the statuses are listed in chapter 3.

<!-- evidence: docs/specs/basket/log.md:96, services/shopping/src/main/java/shop/shopping/basket/BasketItemsController.java:17-30, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:281-298 -->

### 1.2 What this service is not

It is not a catalogue: an unknown offer is a `404`, and the only offers it knows are the ones a deployment
seeds into the fixture that stands in for the catalogue read. It is not a reservation: nothing it does
holds stock, and an add is a statement of intent. And it is not a checkout: handing over a selection does
not create an order, does not empty the basket, and does not lock it.

<!-- evidence: services/shopping/docs/offer-fixture.md:3-7, docs/specs/basket/log.md:112, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:117-126 -->
