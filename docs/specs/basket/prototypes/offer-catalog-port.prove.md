# OfferCatalog port — the read the basket depends on (element 08)

**STATUS: ASSUMPTION.** `services/catalog` has no HTTP surface today (F13) and the HTTP shape of this read
belongs to the browsing slice (D13, Q9). These goldens pin the **shape the basket consumes**; they are
not a contract anyone agreed to serve, and `prove run` becomes meaningful the day that endpoint exists —
`prove validate` and `prove explain` gate them today, without a network.

```bash
prove validate prototypes/offer-catalog-port.prove.md
prove explain  prototypes/offer-catalog-port.prove.md --config prove.yaml --env local --format json
```

There is deliberately **no reservation call** — `reserveProductQuantity` belongs to fulfilment and is not
idempotent (`services/fulfillment/src/main/java/shop/fulfillment/kernel/WarehouseStock.java:29`). A call
like that appearing in the basket path means the spec is being violated (R6, 02 RULE-2).

**vars:**
- catalog_url: http://127.0.0.1:8081
- catalog_token: override-with---var-catalog_token

## 1. Batch read — the only call the basket makes on the offer path

**HTTP request: GET {{catalog_url}}/offers?ids=OF-1001,OF-1002,OF-2007**
Authorization: Bearer {{catalog_token}}

**response:**
Status: 200

```json response
[
  {
    "offerId": "OF-1001",
    "seller": "UGREEN_POLSKA",
    "currentPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "price": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discount": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "unitPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "offerState": "onSale",
    "stock": 12
  },
  {
    "offerId": "OF-1002",
    "seller": "UGREEN_POLSKA",
    "currentPrice": {
      "amount": 64990,
      "currency": "PLN",
      "kind": "charge"
    },
    "price": {
      "amount": 64990,
      "currency": "PLN",
      "kind": "charge"
    },
    "discount": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "unitPrice": {
      "amount": 64990,
      "currency": "PLN",
      "kind": "charge"
    },
    "offerState": "onSale",
    "stock": 0
  },
  {
    "offerId": "OF-2007",
    "seller": "ANKER_STORE",
    "currentPrice": {
      "amount": 129000,
      "currency": "PLN",
      "kind": "charge"
    },
    "price": {
      "amount": 129000,
      "currency": "PLN",
      "kind": "charge"
    },
    "discount": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "unitPrice": {
      "amount": 129000,
      "currency": "PLN",
      "kind": "charge"
    },
    "offerState": "removed",
    "stock": 0
  }
]
```

**ignore:**
- $[*].title
- $[*].pictureUrl

## 2. A requested id that is missing means "no longer on sale" — never a 404 (element 06 A3)

**HTTP request: GET {{catalog_url}}/offers?ids=OF-1001,OF-9999**
Authorization: Bearer {{catalog_token}}

**response:**
Status: 200

```json response
[
  {
    "offerId": "OF-1001",
    "seller": "UGREEN_POLSKA",
    "currentPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "price": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discount": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "unitPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "offerState": "onSale",
    "stock": 12
  }
]
```

**ignore:**
- $[*].title
- $[*].pictureUrl

## 3. Stock unknown — the warehouse did not answer, the offer stays readable (element 06 A5)

**HTTP request: GET {{catalog_url}}/offers?ids=OF-1001**
Authorization: Bearer {{catalog_token}}

**response:**
Status: 200

```json response
[
  {
    "offerId": "OF-1001",
    "seller": "UGREEN_POLSKA",
    "currentPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "price": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discount": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "unitPrice": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "offerState": "onSale",
    "stock": null
  }
]
```

**ignore:**
- $[*].title
- $[*].pictureUrl

## 4. Unauthenticated

**HTTP request: GET {{catalog_url}}/offers?ids=OF-1001**

**response:**
Status: 401

## 5. Provider down — the basket must survive this (element 06 A4, Q24)

The provider's error body is unknown (assumption), so only the status is pinned and `$.error` is ignored.

**HTTP request: GET {{catalog_url}}/offers?ids=OF-1001**
Authorization: Bearer {{catalog_token}}

**response:**
Status: 503

```json response
{}
```

**ignore:**
- $.error
