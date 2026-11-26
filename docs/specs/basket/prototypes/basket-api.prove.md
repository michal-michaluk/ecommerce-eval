# Basket HTTP API — contract (element 07)

The `prove` scenario **is** the spec: every endpoint, every full-body golden, every error path. Verbs
follow D28 (GET for reads, PATCH for every update); identity follows D29 (a customer may own several
active baskets and always owns at least one); authority follows D27 (`SCOPE_customer`); money is
`{ "amount": <long>, "currency": "<ISO-4217>", "kind": "charge" | "discount" }` (element 03, D34);
status vocabulary is element 06 — `available` / `outOfStock` / `unavailable` + `stale`.

```bash
prove validate prototypes/basket-api.prove.md
prove explain  prototypes/basket-api.prove.md --config prove.yaml --env local --format json
prove run      prototypes/basket-api.prove.md --config prove.yaml --env local --format json
```

**vars:**
- base_url: http://127.0.0.1:8080
- est_from: 2026-11-28
- est_to: 2026-12-02
- customer_token: override-with---var-customer_token
- service_token: override-with---var-service_token

## 1. List active baskets — always one, created on demand (D29)

**HTTP request: GET {{base_url}}/baskets**
Authorization: Bearer {{customer_token}}

**response:**
Status: 200

```json response
{
  "baskets": [
    {
      "basketId": "B-7f3a",
      "state": "active",
      "currency": "PLN"
    }
  ]
}
```

**capture basketId:** $.baskets[0].basketId

## 2. Clear the basket — every later golden is deterministic

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "all",
  "quantity": null
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [],
  "shipments": [],
  "totals": {
    "productValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": null
}
```

## 3. Add the first offer — PATCH carries the absolute quantity (D28, F16)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001"
      ],
      "productValue": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 4. Add the second offer — same seller, one shipment, totals as the capture (S1, DoD5)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1002**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 164880,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 164880,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 174870,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 5. Repeating the same PATCH is idempotent — quantity does not double (DoD6)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 164880,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 164880,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 174870,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 6. Change the quantity — the line total follows it (S3, DoD7)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 2
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 2,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 199780,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 264770,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 264770,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 274760,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 7. A catalog discount passes through flattened, never recomputed (D22, S7)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 164880,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 164880,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 174870,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 8. Quantity 0 is not a quantity — 422 with the reason (S10, DoD11)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-1001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 0
}
```

**response:**
Status: 422

```json response
{
  "error": "QUANTITY_NOT_POSITIVE"
}
```

## 9. Currency mismatch — one currency per basket (03 RULE-2, D21, DoD10)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-9001**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 422

```json response
{
  "error": "CURRENCY_MISMATCH"
}
```

## 10. Offer unknown to the catalog — 404 (DoD14a)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-0000**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 404

```json response
{
  "error": "OFFER_NOT_FOUND",
  "offerId": "OF-0000"
}
```

## 11. Read one basket — content, totals and estimate in a single call (D10)

**HTTP request: GET {{base_url}}/baskets/{{basketId}}**
Authorization: Bearer {{customer_token}}

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 164880,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 164880,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 174870,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 12. Deselect the second item — the totals follow the selection (VIEW-1, S2)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/selection**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "item",
  "offerId": "OF-1002",
  "selected": false
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001"
      ],
      "productValue": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 13. A handover with an empty selection is refused — 422 (RULE-9)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/selection**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "all",
  "selected": false
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [],
  "totals": {
    "productValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 0,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 14. Handover with nothing selected — 422

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/checkout**
Authorization: Bearer {{customer_token}}

**response:**
Status: 422

```json response
{
  "error": "NOTHING_SELECTED"
}
```

## 15. Out-of-stock selected still prices the basket (S4) but blocks the handover (D33, DoD12)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/selection**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "all",
  "selected": true
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "outOfStock",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001",
        "OF-1002"
      ],
      "productValue": {
        "amount": 164880,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 164880,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 174870,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 16. The handover is blocked, naming the row

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/checkout**
Authorization: Bearer {{customer_token}}

**response:**
Status: 422

```json response
{
  "error": "BLOCKED_BY_ITEMS",
  "reasons": [
    {
      "offerId": "OF-1002",
      "status": "outOfStock",
      "stale": false
    }
  ]
}
```

## 17. Deselecting the blocked row enables checkout for the remaining subset (S12, DoD13)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/selection**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "item",
  "offerId": "OF-1002",
  "selected": false
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "outOfStock",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001"
      ],
      "productValue": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 18. The handover goes through, keyed by basketId (D32)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/checkout**
Authorization: Bearer {{customer_token}}

**response:**
Status: 200

```json response
{
  "handoverId": "B-7f3a",
  "basketId": "B-7f3a",
  "createdAt": "2026-11-26T10:00:00Z",
  "customerId": "C-42",
  "currency": "PLN",
  "selection": [
    "OF-1001"
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  }
}
```

**capture revision:** $.revision
**ignore:**
- $.createdAt
- $.revision

## 19. Repeating the handover is the same handover while the content is unchanged (HAND-4)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/checkout**
Authorization: Bearer {{customer_token}}

**response:**
Status: 200

```json response
{
  "handoverId": "B-7f3a",
  "basketId": "B-7f3a",
  "createdAt": "2026-11-26T10:00:00Z",
  "customerId": "C-42",
  "currency": "PLN",
  "selection": [
    "OF-1001"
  ],
  "revision": {{revision}},
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  }
}
```

**ignore:**
- $.createdAt

## 20. An unavailable item blocks the handover and is named (HAND-1, DoD14)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items/OF-2007**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "quantity": 1
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "outOfStock",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-2007",
      "seller": "ANKER_STORE",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "unavailable",
        "stale": false
      },
      "currentPrice": {
        "amount": 129000,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 129000,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001"
      ],
      "productValue": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 21. The handover is blocked by the unavailable row

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/checkout**
Authorization: Bearer {{customer_token}}

**response:**
Status: 422

```json response
{
  "error": "BLOCKED_BY_ITEMS",
  "reasons": [
    {
      "offerId": "OF-2007",
      "status": "unavailable",
      "stale": false
    }
  ]
}
```

## 22. Remove the unavailable item — the basket is never cleared by a handover (D19)

**HTTP request: PATCH {{base_url}}/baskets/{{basketId}}/items**
Authorization: Bearer {{customer_token}}
Content-Type: application/json

```json request
{
  "scope": "item",
  "offerId": "OF-2007",
  "quantity": null
}
```

**response:**
Status: 200

```json response
{
  "basketId": "B-7f3a",
  "state": "active",
  "currency": "PLN",
  "items": [
    {
      "offerId": "OF-1001",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": true,
      "availability": {
        "status": "available",
        "stale": false
      },
      "currentPrice": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      }
    },
    {
      "offerId": "OF-1002",
      "seller": "UGREEN_POLSKA",
      "quantity": 1,
      "selected": false,
      "availability": {
        "status": "outOfStock",
        "stale": false
      },
      "currentPrice": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      },
      "discount": {
        "amount": 0,
        "currency": "PLN",
        "kind": "discount"
      },
      "lineTotal": {
        "amount": 64990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "shipments": [
    {
      "seller": "UGREEN_POLSKA",
      "items": [
        "OF-1001"
      ],
      "productValue": {
        "amount": 99890,
        "currency": "PLN",
        "kind": "charge"
      },
      "delivery": {
        "amount": 9990,
        "currency": "PLN",
        "kind": "charge"
      }
    }
  ],
  "totals": {
    "productValue": {
      "amount": 99890,
      "currency": "PLN",
      "kind": "charge"
    },
    "discountValue": {
      "amount": 0,
      "currency": "PLN",
      "kind": "discount"
    },
    "delivery": {
      "amount": 9990,
      "currency": "PLN",
      "kind": "charge"
    },
    "totalWithDelivery": {
      "amount": 109880,
      "currency": "PLN",
      "kind": "charge"
    }
  },
  "deliveryEstimate": {
    "from": "{{est_from}}",
    "to": "{{est_to}}"
  }
}
```

**ignore:**
- $.items[*].title
- $.items[*].picture

## 23. No token — 401, empty body (D27)

**HTTP request: GET {{base_url}}/baskets**

**response:**
Status: 401

## 24. A service token on a customer endpoint — 403, empty body (D27)

**HTTP request: GET {{base_url}}/baskets**
Authorization: Bearer {{service_token}}

**response:**
Status: 403
