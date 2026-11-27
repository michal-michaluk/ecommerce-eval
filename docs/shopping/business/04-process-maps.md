## 4 Process maps

One process crosses this service: a customer assembling a basket and handing it over. The map below names
real route ids, and the two figures show where the service sits and what a handover does.

These maps are diagrams, not BPMN. They carry no pools, no gateways and no message hops, and they are not
conformant to any modelling standard — the shapes are chosen for a reader, not for a tool.

<!-- evidence: docs/specs/basket/index.md:60-74 -->

### 4.1 Where the service sits

![The shopping service between the customer, the catalogue stand-in and the checkout slice.](generated/shopping/diagrams/context.svg){#fig-business-context}

### 4.2 Assembling a basket

![The steps a customer goes through, with the route that carries each one.](generated/shopping/diagrams/process-basket-to-checkout.svg){#fig-business-process}

### 4.3 The steps, with what each one is worth

| Step | Route | What the customer gets | What can refuse it |
|---|---|---|---|
| open the basket | `GET /baskets`, `GET /baskets/{basketId}` | the basket, its totals and its delivery range | `404` for an unknown or foreign basket |
| add an offer | `PATCH …/items/{offerId}` | the basket after the add, pulled | `404` unknown offer, `503` unreadable offer, `422` currency |
| change a quantity | `PATCH …/items/{offerId}` | the basket after the change | `422` quantity below one, `422` unavailable row |
| remove | `PATCH …/items` | the basket after the removal | `404` offer not in the basket |
| tick or untick | `PATCH …/selection` | the basket the new selection prices | `404` offer not in the basket |
| go to checkout | `PATCH …/checkout` | the handover's identity, revision and totals | `422` nothing selected, `422` blocked by items, `422` inactive basket |

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:88-157, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:211-237 -->

### 4.4 What leaves the service, and what does not

Two things cross the boundary of the slice. An **offer observation** comes in — today from a read, later from
an event — and changes what a row says about its offer without changing what the customer asked for. A
**handover** goes out — the selection, its totals and its identity — to the checkout slice, which in this
increment records it and creates nothing.

Neither crossing is asynchronous. There is no broker, no topic and no publisher outside the process, which is
why the customer's own request is what refreshes availability.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/CheckoutHandovers.java:8-20, services/shopping/src/main/java/shop/shopping/basket/BasketService.java:262-271, services/shopping/k8s/base/configmap.yaml:9-10 -->
