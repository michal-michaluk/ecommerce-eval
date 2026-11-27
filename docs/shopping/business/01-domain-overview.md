## 1 Business domain overview

The shopping service owns one step of a purchase: the moment between "I want this" and "I am buying it".
Its customer assembles a basket — offers, quantities, a selection — sees what the selection costs including
delivery, and sees when it would arrive. Everything after that sentence belongs to a checkout that this
increment does not implement.

<!-- evidence: docs/specs/basket/index.md:5-8, docs/specs/basket/index.md:169-171 -->

### 1.1 Who acts, and on what

| Actor | What they do here | What they get back |
|---|---|---|
| Customer | adds an offer, changes a quantity, removes, ticks and unticks, asks to check out | one answer per action: the basket with its content, its totals and its delivery estimate |
| The catalogue (as a counterpart) | describes an offer: title, picture, current price, discount, availability | read by the service on every answering request |
| The checkout step (as a counterpart) | receives the selected rows and their totals | a receipt: the handover's identity, its revision, the totals |

The customer never types a price, never types a total and never sees a message from the service: the
basket answers with amounts and with codes, and the words are the client's to render.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:15-40, services/shopping/src/main/java/shop/shopping/BasketErrorAdvice.java:26-56 -->

### 1.2 The capabilities this service implements

| Capability | What it means in business terms |
|---|---|
| keep a basket | a customer always has one, and it is theirs alone |
| add and change | the same offer is one row whose quantity is set, never a second row |
| remove | one row, the ticked rows, or everything |
| select | ticking decides what is priced and what crosses to checkout |
| price | the selection's value, its discount, the delivery of each seller's shipment, and the total |
| date | one delivery range for the whole basket, counted from today |
| judge availability | each row says whether it can be sold, and whether that answer is fresh |
| hand over | the selection crosses to checkout with its identity, its revision and its totals |

<!-- evidence: docs/specs/basket/index.md:12-23, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:17-40 -->

### 1.3 Where the responsibility ends

The basket decides what the customer intends to buy and what that intent is worth. It does not decide
whether a product exists (the catalogue does), whether stock is available (the catalogue's stock answer
does), whether an order is created (checkout does, later), and whether a price is fair (the catalogue's
flattened price is taken as given). The service also never reserves stock: an add is a statement of intent,
and nothing is held for anybody.

<!-- evidence: docs/specs/basket/index.md:28-31, services/shopping/src/main/java/shop/shopping/basket/BasketEditor.java:18-30 -->

The seam with checkout is worth stating precisely, because it is where the increment stops: handing over
records that this selection crossed, at this revision, for these totals. The basket stays active and
editable, nothing is cleared, nothing is locked, and no order exists anywhere on this side of the seam.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:211-230, services/shopping/src/main/java/shop/shopping/checkout/CheckoutService.java:15-21 -->
