---
name: Business Feature
about: Shopping Experience — browsing, basket, checkout, order creation
labels: business-feature
---

## Business Context

Shopping Experience is the customer-facing domain for product browsing, basket management, and checkout. It consumes the product catalog (via events) and maintains local stock read models (updated by warehouse events), integrates with delivery companies, and payment providers.

### Background
- Customers search and browse published products via all channels (web, mobile, call center)
- Product details include: description, current price, and stock (from local read model updated by warehouse events)
- Basket does not reserve products by default
- Items removed from the offer remain in basket but are flagged as unavailable
- Out-of-stock items can still be ordered but with longer delivery time
- Delivery methods are filtered based on basket content
- Payment methods are filtered based on customer history
- Checkout is a guarded action — all selections must be valid
- Order is immutable after checkout
- CRM stores customer and invoicing data

### Key Goals
1. Provide customers with responsive, always-scalable browsing across channels
2. Provide delivery time, cost, and payment methods visible throughout shopping & checkout
3. Process checkout with basket-dependent delivery and payment eligibility rules
4. Create an immutable order record

### Stakeholders
- Customer (browse, add to basket, checkout)
- Operations (manage order issues)
- CRM (receives order data)

---

## Actors & Interactions

### Customer
- Searches products via catalog read models
- Views product details (description + current price + stock from local read model)
- Adds items to basket
- Changes quantity / removes items
- Views basket summary (price, discounts, estimated delivery, stock issues)
- Proceeds to checkout
- Selects delivery method from filtered options
- Selects payment method from filtered options
- Fills customer data (delivery, invoicing)
- Processes checkout
- Receives order confirmation
- Receives order tracking updates

### External Systems
- **Catalog Read Model** — provides product descriptions, prices, availability
- **Warehouse System** — emits stock change events, provides stock data (via events to local read model)
- **Delivery Company** — provides available delivery methods, tracks shipment
- **Payment Provider** — provides payment methods, eligibility, processes payment
- **CRM** — provides customer data, receives order history

---

## Key Calculations / Decisions

| Name | Trigger | Inputs | Output |
|------|---------|--------|--------|
| **Basket Calculation** | basket change | items + prices + discounts + stock status | price summary + estimated delivery + issues |
| **Delivery Time Estimation** | basket change / delivery selection | basket content + delivery method + stock availability + warehouse location | estimated delivery date/window |
| **Delivery Method Eligibility** | checkout view | basket content (dimensions, weight, item types) | feasible delivery methods |
| **Payment Method Eligibility** | checkout view | customer history + partner checks + order total | allowed payment methods |
| **Checkout Guard** | checkout action | all selections + basket validity + customer data | proceed / blocked + reasons |
| **Discount Calculation** | basket change | items + discount rules | discount amount |

---

## Artifacts

- **Basket** — customer session, contains items, quantities, calculated totals
- **Order** — immutable document created at checkout. Contains: customer, items, delivery, payment, totals
- **Order Confirmation** — customer-facing summary of the order
- **Checkout View** — filtered delivery + payment options + customer data

---

## API Contract (FE vs BE)

### Search Products
```
GET /products/search?q={query}&page={page}&size={size}&status={status}
                         &category={category}

Response:
{
  "products": [
    {
      "productId": "75423",
      "title": "LEGO Star Wars X-Wing",
      "price": 379.99,
      "currency": "PLN",
      "stock": 150,
      "availability": "IN_STOCK | LOW_STOCK | OUT_OF_STOCK",
      "estimatedDelivery": "1-2 days"
    }
  ],
  "total": 100,
  "page": 0,
  "size": 20
}
```

### View Product Details
```
GET /products/{productId}

Response:
{
  "productId": "75423",
  "title": "LEGO Star Wars X-Wing",
  "description": "...",
  "price": 379.99,
  "currency": "PLN",
  "stock": 150,
  "availability": "IN_STOCK",
  "estimatedDelivery": "1-2 days"
}
```

### Get Basket
```
GET /basket/{customerId}

Response:
{
  "basketId": "uuid",
  "items": [
    {
      "productId": "75423",
      "quantity": 2,
      "unitPrice": 379.99,
      "totalPrice": 759.98,
      "availability": "IN_STOCK | LOW_STOCK | OUT_OF_STOCK | UNAVAILABLE",
      "issue": {"type": "OUT_OF_STOCK | REMOVED_FROM_OFFER", "severity": "BLOCKING | NON_BLOCKING", "message": "string"}
    }
  ],
  "subtotal": 759.98,
  "discount": 0,
  "total": 759.98,
  "deliveryEstimate": "1-2 days",
  "issues": [
    {"severity": "NON_BLOCKING", "type": "OUT_OF_STOCK", "message": "string"},
    {"severity": "BLOCKING", "type": "PRICE_CHANGED | STOCK_DROPPED | CART_EXPIRED", "message": "string"}
  ]
}
```

### Add to Basket
```
POST /basket/{customerId}/items

Request:
{
  "productId": "75423",
  "quantity": 2
}

Response:
{
  "basketId": "uuid",
  "items": [...],
  "subtotal": 759.98,
  "total": 759.98,
  "deliveryEstimate": "1-2 days"
}
```

### Update Basket Item
```
PATCH /basket/{customerId}/items/{productId}

Request:
{
  "quantity": 3
}

Response:
{
  "basketId": "uuid",
  "items": [...],
  "subtotal": 1139.97,
  "total": 1139.97
}
```

### Remove from Basket
```
DELETE /basket/{customerId}/items/{productId}

Response:
{
  "basketId": "uuid",
  "items": [...],
  "subtotal": 0,
  "total": 0
}
```

### Get Checkout Options
```
GET /checkout/{customerId}

Response:
{
  "basketId": "uuid",
  "subtotal": 759.98,
  "discount": 0,
  "total": 759.98,
  "deliveryOptions": [
    {
      "method": "HOME_DELIVERY",
      "name": "Home Delivery",
      "cost": 15.00,
      "estimatedDays": 2,
      "eligible": true
    },
    {
      "method": "PICKUP_POINT",
      "name": "Paczkomat",
      "cost": 10.00,
      "estimatedDays": 1,
      "eligible": false,
      "ineligibilityReason": "BASKET_TOO_LARGE"
    }
  ],
  "paymentOptions": [
    {
      "method": "CREDIT_CARD",
      "name": "Credit Card",
      "eligible": true
    },
    {
      "method": "DEFERRED_PAYMENT",
      "name": "Pay in 30 days",
      "eligible": false,
      "ineligibilityReason": "CUSTOMER_HISTORY"
    }
  ],
  "customerData": {
    "previousAddresses": [...],
    "previousInvoicing": [...]
  }
}
```

### Apply Discount Code
```
POST /checkout/{customerId}/discount

Request:
{
  "code": "LEGO10"
}

Response:
{
  "discountId": "uuid",
  "type": "PERCENTAGE | FIXED_AMOUNT",
  "value": 10,
  "discountedAmount": 121.99,
  "totalAfterDiscount": 1097.98,
  "errors": {"code": "INVALID_CODE | EXPIRED | NOT_ELIGIBLE", "message": "string"}
}
```

### Start Guest Session
```
POST /sessions/guest

Request:
{
  "email": "guest@example.com"
}

Response:
{
  "customerId": "uuid",
  "sessionToken": "string",
  "isGuest": true
}
```

### Process Checkout
```
POST /checkout/{customerId}

Request:
{
  "deliveryMethod": "HOME_DELIVERY",
  "deliveryAddress": {
    "street": "...",
    "city": "...",
    "postalCode": "...",
    "country": "PL"
  },
  "invoicingData": {
    "email": "...",
    "name": "...",
    "taxId": "...",
    "address": {
      "street": "...",
      "city": "...",
      "postalCode": "...",
      "country": "PL"
    }
  },
  "paymentMethod": "CREDIT_CARD",
  "paymentData": {
    "token": "..."
  },
  "discountId": "uuid",
  "consentToSaveData": true
}

Response:
{
  "orderId": "uuid",
  "status": "CONFIRMED",
  "total": 1112.98,
  "subtotal": 1219.97,
  "discount": 121.99,
  "deliveryCost": 15.00,
  "items": [...],
  "delivery": {
    "method": "HOME_DELIVERY",
    "address": {...},
    "estimatedDelivery": "2026-07-03"
  },
  "payment": {...},
  "returnPolicy": "Free returns within 14 days",
  "createdAt": "timestamp"
}
```

---

## In Scope

- Product search and browsing
- Product details (description + price + stock)
- Basket management (add, change quantity, remove)
- Basket calculation (subtotal + discount + total + delivery estimate)
- Delivery method filtering (basket-dependent eligibility)
- Payment method filtering (customer-dependent eligibility)
- Checkout flow (delivery + payment + customer data)
- Checkout guard (validation of all selections)
- Order creation (immutable order)
- Discount calculation (simple percentage, fixed amount)
- Customer data integration (pre-filled from CRM)
- Guest checkout (optional — if customer data is provided)
- Domain model with unit tests
- Hexagonal vertical slice (shopping-service)
- API controller fulfilling contract
- Event publishing (order created)
- NoSQL persistence

## Out of Scope

- Payment processing (redirect to payment provider)
- CRM integration (customer data, order history) — assumed via API
- Direct warehouse stock API queries — stock consumed via events to local read model
- Delivery company integration (shipping, tracking) — happens in Fulfillment
- Product catalog management (creation, review, publish) — in Product Catalog
- Full promotions engine (complex stacking, coupon codes)
- Real-time basket sync across devices
- Basket persistence across sessions
- Wishlist / favorites
- Product recommendations
- Reviews and ratings
- Address validation (assumed via 3rd party)

---

## Open Questions

- Guest checkout allowed? Or must create account?
- Discount types: simple percentage, fixed amount, or more?
- Discount stacking: one at a time or multiple?
- CRM integration: what fields are pre-filled? Guest checkout flow?
- Delivery eligibility: exact rules (dimensions, weight, hazardous goods)
- Payment eligibility: concrete checks (credit score, order total, partner history)
- Checkout guard: what scenarios are checked? (price changed, stock dropped, cart expired?)
- Basket persistence: guest basket survives session? expires?
- Delivery time calculation: exact formula (stock + warehouse + method + calendar)
- Out-of-stock items: estimated delivery time based on restock? backorder?
- Order numbering: sequential, UUID-based?

---

# Shopping — Gherkin Scenarios

```gherkin
Feature: Product Browsing
  As a customer
  I want to search and view products
  So that I can find what I want to buy

  Scenario: Search products
    Given products are published in the catalog
    When the customer searches for "LEGO"
    Then products matching "LEGO" are returned
    And each product shows title, price, and stock

  Scenario: View product details
    Given a product "75423" is published
    When the customer views product "75423"
    Then the product details are displayed
    And the current price is shown
    And the stock availability is shown

  Scenario: Product out of stock
    Given a product "75423" is published
    And the stock is 0
    When the customer views product "75423"
    Then the stock is shown as "OUT_OF_STOCK"
    And the estimated delivery is shown as "available on restock"

Feature: Basket Management
  As a customer
  I want to add, change, and remove items
  So that I can manage my purchase

  Background:
    Given a customer "C1" has an empty basket

  Scenario: Add item to basket
    Given product "75423" has price 379.99 PLN and is in stock
    When the customer adds 2 units of "75423" to the basket
    Then the basket contains 2 units of "75423"
    And the basket subtotal is 759.98

  Scenario: Change quantity
    Given the basket contains 2 units of "75423"
    When the customer changes quantity to 3
    Then the basket subtotal is 1139.97

  Scenario: Remove item
    Given the basket contains 2 units of "75423"
    When the customer removes "75423"
    Then the basket is empty
    And the subtotal is 0

  Scenario: Product removed from offer
    Given the basket contains product "75423"
    When the product "75423" is removed from the offer
    Then the product is still in the basket
    And it is marked as unavailable
    And the customer is warned

  Scenario: Product out of stock in basket
    Given the basket contains 10 units of "75423"
    And the stock is 5
    Then the product is still in the basket
    And it is marked as out of stock
    And the customer is warned

Feature: Checkout
  As a customer
  I want to select delivery, payment, and confirm the order
  So that I can complete my purchase

  Background:
    Given a customer "C1" has items in the basket

  Scenario: View delivery options
    When the customer proceeds to checkout
    Then delivery options are shown
    And options are filtered by basket eligibility
    And the cost and estimated delivery are shown

  Scenario: View payment options
    When the customer proceeds to checkout
    Then payment options are shown
    And options are filtered by customer eligibility

  Scenario: Successful checkout
    Given the customer selects "HOME_DELIVERY" as delivery method
    And the customer selects "CREDIT_CARD" as payment method
    And the customer provides valid delivery and invoicing data
    When the customer processes checkout
    Then the order is created
    And the order is immutable
    And the order is confirmed

  Scenario: Checkout with invalid delivery method
    Given the customer selects "PICKUP_POINT" for a basket with a washing machine
    When the customer processes checkout
    Then the checkout is blocked
    And the error is "DELIVERY_METHOD_NOT_ELIGIBLE"

  Scenario: Checkout with invalid payment method
    Given the customer selects "DEFERRED_PAYMENT" with restricted customer history
    When the customer processes checkout
    Then the checkout is blocked
    And the error is "PAYMENT_METHOD_NOT_ELIGIBLE"

  Scenario: Returning customer with pre-filled data
    Given the customer is a returning customer
    When the customer proceeds to checkout
    Then previous delivery addresses are pre-filled
    And previous invoicing data is pre-filled

  Scenario: Order with discount
    Given a 10% discount is active
    And the basket subtotal is 759.98
    When the customer processes checkout
    Then the discount is applied
    And the order total is 683.98

  Scenario: Apply coupon code
    Given customer "C1" has items in the basket
    When the customer enters coupon code "LEGO10"
    And the code is valid
    Then the discount is applied
    And the total is updated

  Scenario: Invalid coupon code
    Given customer "C1" has items in the basket
    When the customer enters invalid coupon code "INVALID"
    Then an error "INVALID_CODE" is returned
    And the total is unchanged

  Scenario: Empty basket cannot checkout
    Given customer "C1" has an empty basket
    When the customer tries to proceed to checkout
    Then the checkout is blocked
    And the error is "BASKET_EMPTY"

  Scenario: Checkout blocked due to blocker issue
    Given customer "C1" has a product in basket that was removed from the offer
    When the customer tries to proceed to checkout
    Then the checkout is blocked
    And the error is "CART_HAS_BLOCKING_ISSUES"

  Scenario: Guest checkout flow
    Given the customer is a new visitor
    When the customer proceeds to checkout
    Then the customer can choose between sign in and guest checkout
    When the customer chooses guest checkout and provides email
    Then a guest session is created

Feature: Order Confirmation
  As a customer
  I want to see my order confirmation
  So that I know my order was successful

  Scenario: View order confirmation
    Given the customer has placed an order
    When the order is confirmed
    Then the order number is displayed
    And the delivery timeline is shown
    And the total is displayed
```
