## 11 Response bodies

This repository declares no schema for a response body, so the tables below are read from the records the
handlers answer with. They are the field-by-field contract a client needs; where the wire form differs from
the record, the record's own documentation says why, and the table repeats it.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:15-40, services/shopping/src/main/java/shop/shopping/basket/HandoverAnswer.java:9-28 -->

### 11.1 The basket

Every route that answers basket content answers this object: the read route and all three command routes.
The list route answers the smaller object in §11.6 instead.

| Field | Type | Always present | Meaning |
|---|---|---|---|
| `basketId` | string | yes | the identity every path carries |
| `state` | `active` \| `archived` | yes | whether the basket is still editable; only `active` exists in this increment |
| `currency` | string | yes | the one currency the basket holds; an offer in another is refused |
| `items[]` | array of row | yes, possibly empty | every row the basket holds, selected or not, in the order they were added |
| `shipments[]` | array of shipment | yes, possibly empty | one group per seller that has at least one counted row |
| `totals` | object | yes | what the selection is worth, as §11.4 |
| `deliveryEstimate` | object or `null` | yes | the inclusive day range, or `null` when there is nothing handable over to estimate |

: The basket content object.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:41-48, services/shopping/src/main/java/shop/shopping/basket/BasketState.java:8-10 -->

### 11.2 A row

| Field | Type | Meaning |
|---|---|---|
| `offerId` | string | the offer the row stands for |
| `seller` | string | the seller, which is the shipment the row is grouped into |
| `title` | string | the product title, as the last read described it |
| `picture` | string | one picture URL |
| `quantity` | integer | units, always at least one; zero is not a quantity |
| `selected` | boolean | whether the customer's selection covers this row |
| `availability.status` | `available` \| `outOfStock` \| `unavailable` | what the basket is allowed to say about the offer |
| `availability.stale` | boolean | whether what the row shows is last-known rather than freshly read |
| `currentPrice` | amount | the price one unit is sold at, as the catalog flattened it |
| `discount` | amount | the catalog's flattened discount for one unit, reported and not subtracted a second time |
| `lineTotal` | amount | `currentPrice` at `quantity` — what the row is worth here, not a unit price |

: One row of a basket.

`availability` carries the status and the freshness and nothing else: the reason behind a status is the
basket's business, and a client renders a status rather than branching on why.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:117-144, services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:157-169 -->

### 11.3 A shipment, the totals and the estimate

| Object | Field | Type | Meaning |
|---|---|---|---|
| shipment | `seller` | string | the seller every row of the group belongs to |
| shipment | `items[]` | array of string | the offer ids that cross in this shipment |
| shipment | `productValue` | amount | the rows' line totals, summed |
| shipment | `delivery` | amount | one rate for this shipment, zero on the SMART channel |
| totals | `productValue` | amount | every counted row's line total, summed |
| totals | `discountValue` | amount | every counted row's discount, summed, reported beside the price |
| totals | `delivery` | amount | the sum of the shipments' rates |
| totals | `totalWithDelivery` | amount | `productValue` plus `delivery` |
| deliveryEstimate | `from`, `to` | date | an inclusive range of calendar days; `null` instead of the whole object when nothing is handable over |

: The grouped, summed and dated parts of the basket.

A counted row is a selected row that is not `unavailable`. An `outOfStock` row counts while it is selected —
its price is real, and blocking the handover is the decision of the handover, not of the price. A
`unavailable` row counts for nothing, because a failed read may not silently keep an earlier price in the
total.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketPriceCalculation.java:44-71, services/shopping/src/main/java/shop/shopping/basket/BasketContent.java:87-115 -->

### 11.4 How an amount is written

An amount is an object of three components, and the third one is not decoration:

| Component | Type | Meaning |
|---|---|---|
| `amount` | integer | units one digit finer than the currency's minor unit: PLN has two decimals, so `99890` is `99,89 zł` |
| `currency` | string | the ISO-4217 code the amount is in |
| `kind` | `charge` \| `discount` | whether the amount adds to what the customer pays or is the discount reported beside it |

Nothing in a body is rounded: the fine unit is the wire unit, and a client that displays money applies the
one half-up step itself.

<!-- evidence: services/shopping/src/main/java/shop/shopping/kernel/Money.java:30-44, services/shopping/src/main/java/shop/shopping/kernel/Money.java:110-135 -->

### 11.5 The handover answer

`PATCH …/checkout` answers a projection of what crossed, not the payload itself.

| Field | Type | Meaning |
|---|---|---|
| `handoverId` | string | the handover's identity, which is the basket's |
| `basketId` | string | the basket it was composed from |
| `createdAt` | timestamp | when it was composed; the same instant for a repeated handover |
| `customerId` | string | the owner, as the aggregate stores it — never read from the token |
| `currency` | string | the one currency the basket holds |
| `selection[]` | array of string | the offer ids that crossed, sorted, so two answers of one content are one answer |
| `revision` | integer | the revision of the content that crossed, echoed on a repeat |
| `totals` | object | what the customer pays for that selection, as §11.3 |

: The answer to a handover.

Two fields a client might expect are deliberately absent: the delivery estimate, because checkout re-prices
anyway, and checkout's own identifier, because the answer to a customer is what the basket hands over and
not what the receiving slice called it.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/HandoverAnswer.java:9-28, services/shopping/src/main/java/shop/shopping/basket/HandoverAnswer.java:49-60 -->

### 11.6 The list

`GET /baskets` answers one key, an array. A row is an identity with a state, not a summary of content:
copying the content here would make this route a second place where selection and availability could
disagree with the basket.

| Field | Type | Meaning |
|---|---|---|
| `baskets[]` | array | one row per active basket, the created one included |
| — `basketId` | string | the identity every path carries |
| — `state` | `active` \| `archived` | whether the basket is still editable |
| — `currency` | string | the one currency the basket holds |

: The caller's active baskets.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/BasketsController.java:34-40, services/shopping/src/main/java/shop/shopping/basket/BasketSummary.java:5-16, services/shopping/src/main/java/shop/shopping/basket/BasketSummary.java:17 -->
