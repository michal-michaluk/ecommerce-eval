## 5 Business impact

What stops when this service stops, and for how long it may stay stopped, are the two questions a business
owner asks. The repository answers the first. It answers the second nowhere, and this chapter says so rather
than estimating.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:8-8, services/shopping/src/main/resources/db/changelog/0001-basket.yaml:3-39 -->

### 5.1 What stops

| Function | What a customer loses | What survives |
|---|---|---|
| reading a basket | the basket page: content, totals, the delivery range | nothing is lost — the document is in the database |
| adding, changing, removing, ticking | every basket edit, with a `5xx` or a connection error | the last stored basket |
| handing over to checkout | the transition into checkout | handovers already recorded; the basket stays active |
| the management surface | health, metrics, the environment view | — |

A basket is not a reservation, so a stopped service loses no stock and no promise: the offers are the
catalogue's and the customer's intent is stored, not held.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketService.java:64-157, services/shopping/src/main/java/shop/shopping/kernel/CheckoutHandovers.java:20-30 -->

### 5.2 How long it may stay stopped

**GAP-B1 — No MTPD, RTO or RPO exists.** No material states a maximum tolerable period of disruption, a
recovery-time objective or a recovery-point objective for this service, and the deployment declares one
replica, so a pod restart is the whole of the availability story. The specification records that no volume,
latency or availability number exists in any source, and this chapter will not invent one. Closing it means
the business owner stating the three numbers; the technical half — what a restart costs — is in §5.3 and in
the operations manual.

<!-- evidence: docs/specs/basket/index.md:8, docs/specs/basket/index.md:173, services/shopping/k8s/base/deployment.yaml:7-25 -->

### 5.3 What a restart costs, in business terms

- **Stored baskets survive.** The aggregate is one document per basket, written before any event is
  published, so a restarted pod reads the same content, quantities, selection and totals.
- **A derived availability is re-decided.** The fixture that answers offer reads keeps its stock queue in the
  process, so after a restart a row that had gone out of stock can come back as available until the
  catalogue is asked again. The specification records this as the limit of what the restart row claims, and
  it is the one behaviour a business reader must know: a customer can see a basket change its availability
  because the service restarted, not because the offer changed.

<!-- evidence: docs/specs/basket/prove-of-done.md:66-66, services/shopping/docs/offer-fixture.md:100-110, services/shopping/src/main/java/shop/shopping/basket/DocumentBasketRepository.java:60-70 -->

### 5.4 The one business rule that is a fraud decision

An offer that cannot be read makes its row unavailable and blocks the handover, instead of letting the
customer buy at a stale price. That is a deliberate business decision with a cost — a catalogue outage
blocks checkout for the affected customers — and the specification records that it overrides the board's
"out of stock is non-blocking" reading. A business owner who disagrees is disagreeing with a recorded
decision, not with the code.

<!-- evidence: docs/specs/basket/log.md:103, docs/specs/basket/log.md:113 -->
