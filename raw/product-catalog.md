---
name: Business Feature
about: Product Catalog Management — creation, review, publication, and pricing
labels: business-feature
---

## Business Context

Product Catalog Management is the core domain for creating and publishing product descriptions and prices. It begins with a Content Manager creating a blank product draft and ends with a published product version visible to customers across all channels.

### Background
- Event Storming session defines the product creation flow from blank draft to published offer
- Product descriptions are private until explicitly published
- Photos are added separately by dedicated photo personnel
- Review is required by another Content Manager to ensure quality
- Prices are managed separately by Sales and can be scheduled for future dates
- A new product version (or new product) can be created at any time
- An offer may have a start-of-availability date in the future

### Key Goals
1. Streamline product catalog creation with draft → review → publish workflow
2. Ensure high-quality product descriptions through quality gates
3. Enable scheduled publishing of offers and prices with future availability dates
4. Provide private draft workspace for iterative product description work

### Stakeholders
- Content Manager (creates, edits, requests review)
- Photo Personnel (uploads photos)
- Reviewer (validates and approves)
- Sales Personnel (sets prices)
- Customer (reads published product details)

---

## Actors & Interactions

### Content Manager
- Creates a blank product draft (title, product IDs)
- Edits product description (title, marketing text, structured content)
- Associates photos with product
- Requests review when done
- Sets or changes scheduled availability date
- Receives quality gate feedback (missing fields, policy issues)

### Photo Personnel
- Uploads high-resolution product photos
- Associates photos with product drafts
- Photos are stored but not published yet

### Reviewer (another Content Manager)
- Receives pending review notification
- Reviews draft description
- Validates against quality policy
- Approves or rejects
- Can request changes

### Sales Personnel
- Sets product price (with optional date range)
- Sets discount rules
- Price changes are private until published/scheduled
- On activation, emits price-changed event to outside world

### External Systems
- Catalog Read Models (all channels consume: web, mobile, call center)
- Price Events (consumed by other services)

---

## Key Calculations / Decisions

| Name | Trigger | Inputs | Output |
|------|---------|--------|--------|
| **Description Quality Gate** | review request | description content + policy rules | pass / fail / rejection reasons |
| **Product Visibility** | scheduled date reached | product description + price + availability date | visible / hidden in catalog |
| **Price Activation** | schedule date reached | price + effective date range | active price + price changed event |
| **Version Creation** | content manager intent | existing product + new data | new draft version |

---

## Artifacts

- **Product Description** — draft → published. Contains: product title, IDs, marketing text, structured attributes, photos
- **Price Schedule** — contains: product reference, price, currency, effective date range, end date
- **Draft Read Model** — private view for content team (shows drafts, review status, pending reviews)
- **Published Product Read Model** — public view for all channels (shows active description, price)

---

## API Contract (FE vs BE)

### List Products
```
GET /products?status={status}&page={page}&size={size}&q={query}

Response:
{
  "products": [
    {
      "productId": "uuid",
      "title": "string",
      "setNumber": "string",
      "productLine": "string",
      "series": "string",
      "subSeries": "string",
      "status": "DRAFT | PENDING_REVIEW | APPROVED | SCHEDULED | PUBLISHED",
      "version": "string",
      "createdBy": "string",
      "createdAt": "timestamp",
      "lastModifiedBy": "string",
      "lastModifiedAt": "timestamp",
      "scheduledDate": "timestamp"
    }
  ],
  "total": 50,
  "page": 0,
  "size": 20
}
```

### Get Product Details
```
GET /products/{productId}

Response:
{
  "productId": "uuid",
  "title": "string",
  "setNumber": "string",
  "ean": "string",
  "series": "string",
  "subSeries": "string",
  "productLine": "string",
  "category": "string",
  "marketingText": "string",
  "ageRecommendation": "string",
  "pieceCount": "int",
  "dimensions": {"heightCm": 6, "lengthCm": 22, "widthCm": 19},
  "battery": {"lifePerChargeMinutes": 45, "includesCharger": true},
  "releaseDate": "timestamp",
  "dataPrivacyStatus": "string",
  "status": "DRAFT | PENDING_REVIEW | APPROVED | SCHEDULED | PUBLISHED",
  "version": "string",
  "createdBy": "string",
  "createdAt": "timestamp",
  "lastModifiedBy": "string",
  "lastModifiedAt": "timestamp",
  "photos": [{"photoId": "uuid", "url": "string", "status": "PUBLISHED | UNPUBLISHED"}]
}
```

### Get Product Reviews
```
GET /products/{productId}/reviews?status={status}&page={page}&size={size}

Response:
{
  "reviews": [
    {
      "reviewId": "uuid",
      "status": "PENDING | APPROVED | REJECTED",
      "requestedBy": "string",
      "requestedAt": "timestamp",
      "reviewedBy": "string",
      "reviewedAt": "timestamp",
      "decision": "APPROVED | REJECTED",
      "comments": "string",
      "qualityGateIssues": ["string"]
    }
  ],
  "total": 10,
  "page": 0,
  "size": 20
}
```

### Get Price Schedule
```
GET /products/{productId}/prices

Response:
{
  "activePrice": {
    "priceId": "uuid",
    "price": 379.99,
    "currency": "PLN",
    "effectiveFrom": "timestamp",
    "effectiveTo": "timestamp",
    "discount": {"type": "PERCENTAGE | FIXED_AMOUNT", "value": 10, "effectiveFrom": "timestamp", "effectiveTo": "timestamp"},
    "status": "ACTIVE"
  },
  "scheduledPrices": [
    {
      "priceId": "uuid",
      "price": 341.99,
      "currency": "PLN",
      "effectiveFrom": "timestamp",
      "effectiveTo": "timestamp",
      "discount": {"type": "PERCENTAGE", "value": 10},
      "status": "SCHEDULED | DRAFT"
    }
  ],
  "priceHistory": [
    {
      "priceId": "uuid",
      "price": 349.99,
      "currency": "PLN",
      "effectiveFrom": "timestamp",
      "effectiveTo": "timestamp",
      "duration": "106 days",
      "reason": "string"
    }
  ]
}
```

### Get Version History
```
GET /products/{productId}/versions

Response:
{
  "versions": [
    {
      "version": "v1.0",
      "status": "PUBLISHED | DRAFT",
      "createdAt": "timestamp",
      "publishedAt": "timestamp",
      "changes": "string"
    }
  ]
}
```

### Get Publishing Audit Log
```
GET /products/{productId}/audit-log

Response:
{
  "entries": [
    {
      "event": "string",
      "user": "string",
      "date": "timestamp",
      "details": "string"
    }
  ]
}
```

### Create Product Draft
```
POST /products

Request:
{
  "title": "string",
  "productIds": ["string"],
  "category": "string"
}

Response:
{
  "productId": "uuid",
  "title": "string",
  "status": "DRAFT",
  "version": "v1",
  "createdBy": "string",
  "createdAt": "timestamp"
}
```

### Update Product Description
```
PATCH /products/{productId}/description

Request:
{
  "title": "string",
  "setNumber": "string",
  "series": "string",
  "subSeries": "string",
  "productLine": "string",
  "marketingText": "string",
  "structuredContent": {},
  "photos": ["photoId"]
}

Response:
{
  "productId": "uuid",
  "version": "string",
  "status": "DRAFT",
  "lastModifiedBy": "string",
  "lastModifiedAt": "timestamp",
  "descriptionUpdatedAt": "timestamp"
}
```

### Update Product Specifications
```
PATCH /products/{productId}/specifications

Request:
{
  "ean": "string",
  "ageRecommendation": "string",
  "pieceCount": "int",
  "dimensions": {"heightCm": 6, "lengthCm": 22, "widthCm": 19},
  "battery": {"lifePerChargeMinutes": 45, "includesCharger": true},
  "dataPrivacyStatus": "string",
  "releaseDate": "timestamp"
}

Response:
{
  "productId": "uuid",
  "version": "string",
  "status": "DRAFT",
  "specificationsUpdatedAt": "timestamp"
}
```

### Update Product Categorization
```
PATCH /products/{productId}/categorization

Request:
{
  "productLine": "string",
  "series": "string",
  "subSeries": "string",
  "categoryTags": ["string"]
}

Response:
{
  "productId": "uuid",
  "version": "string",
  "status": "DRAFT"
}
```

### Upload Product Photo
```
POST /products/{productId}/photos

Request:
multipart/form-data

Response:
{
  "photoId": "uuid",
  "url": "string",
  "status": "UNPUBLISHED"
}
```

### Request Review
```
POST /products/{productId}/reviews

Request:
{
  "requesterId": "string",
  "reviewerId": "string",
  "notes": "string"
}

Response:
{
  "reviewId": "uuid",
  "status": "PENDING",
  "reviewerId": "string",
  "requestedAt": "timestamp",
  "qualityGateIssues": [
    {"severity": "WARNING | BLOCKER", "field": "string", "message": "string"}
  ]
}
```

### Review / Approve
```
POST /products/{productId}/reviews/{reviewId}

Request:
{
  "reviewerId": "string",
  "decision": "APPROVED | REJECTED",
  "comments": "string"
}

Response:
{
  "reviewId": "uuid",
  "status": "APPROVED",
  "reviewedBy": "string",
  "reviewedAt": "timestamp",
  "qualityGateIssues": [{"severity": "BLOCKER", "field": "string", "message": "string"}]
}
```

### Publish Product
```
POST /products/{productId}/publish

Request:
{
  "availabilityDate": "2026-07-01",
  "publishDescription": true,
  "publishPhotos": true,
  "publishPrices": ["priceId"],
  "notes": "string"
}

Response:
{
  "productId": "uuid",
  "version": "string",
  "status": "PUBLISHED | SCHEDULED",
  "availabilityDate": "2026-07-01",
  "publishedAt": "timestamp",
  "publishedBy": "string"
}
```

### Set Price
```
POST /products/{productId}/prices

Request:
{
  "price": 379.99,
  "currency": "PLN",
  "effectiveFrom": "2026-07-01",
  "effectiveTo": "2026-12-31",
  "discount": {
    "type": "PERCENTAGE | FIXED_AMOUNT",
    "value": 10,
    "effectiveFrom": "2026-07-01",
    "effectiveTo": "2026-07-07"
  }
}

Response:
{
  "priceId": "uuid",
  "status": "SCHEDULED | DRAFT",
  "effectiveFrom": "2026-07-01"
}
```

---

## In Scope

- Product creation and draft management
- Product description editing (title, marketing text, structured content)
- Photo upload and association
- Review workflow (request, review, approve/reject)
- Description quality gate (hardcoded policy rules)
- Product versioning
- Scheduled publishing with availability date
- Price scheduling and activation
- Private draft read model
- Public product read model
- Domain model with unit tests
- Hexagonal vertical slice (catalog-service)
- API controller fulfilling contract
- NoSQL persistence

## Out of Scope

- Photo editing / AI processing (assumed done before upload)
- Pluggable quality policy engine (v1 is hardcoded)
- Multi-currency support (v1: PLN only)
- Full promotions engine (v1: simple percentage discounts)
- Photo CDN / variant generation
- Image alt text / accessibility
- Photo organization (albums, categories)
- Price history / audit log
- Batch operations
- Integration with warehouse stock
- CRM integration

---

## Open Questions

- Quality policy: exact rules (mandatory fields, grammar, image resolution) to be defined
- Photo management: organization, variants, alt text — deferred to v2
- Discount types: simple percentage, or fixed amount? Stacking?
- Review: mandatory or optional? Can creator skip? Rework cycle?
- Versioning: what triggers a new version vs edit existing?
- Scheduled publishing: can cancel/edit while waiting?
- Guest catalog browsing: yes, no authentication required

---

# Product Catalog — Gherkin Scenarios

```gherkin
Feature: Product Creation
  As a content manager
  I want to create and edit product drafts
  So that product descriptions can be prepared before publication

  Background:
    Given a content manager is logged in

  Scenario: Create blank product draft
    When the content manager creates a product with title "LEGO X-Wing" and product ID "75423"
    Then a product draft is created with status "DRAFT"
    And the product has a generated version number

  Scenario: Edit product description
    Given a product draft "75423" exists
    When the content manager updates description with marketing text "LEGO Star Wars SMART Play"
    Then the product description is updated
    And the description update event is private

  Scenario: Upload product photo
    Given a product draft "75423" exists
    When photo personnel uploads photo "xwing_front.jpg"
    Then the photo is stored with status "UNPUBLISHED"
    And the photo is associated with the product draft

Feature: Review Workflow
  As a content manager
  I want to request and perform reviews
  So that product descriptions meet quality standards

  Scenario: Request review
    Given a product draft "75423" exists with description content
    When the content manager requests review
    Then the review status is "PENDING"
    And another content manager is notified

  Scenario: Approve review
    Given a product "75423" has a pending review
    When the reviewer approves with no quality issues
    Then the review is approved
    And the product is ready for publication

  Scenario: Reject review
    Given a product "75423" has a pending review
    And the description has missing mandatory fields
    When the reviewer validates against quality policy
    Then the review is rejected
    And the quality gate issues are returned

Feature: Product Publication
  As a content manager
  I want to publish products with availability dates
  So that product descriptions are visible to customers

  Scenario: Publish immediately
    Given a product "75423" is approved
    When the content manager publishes with availability date today
    Then the product status is "PUBLISHED"
    And the product is visible in catalog

  Scenario: Publish with future availability
    Given a product "75423" is approved
    When the content manager publishes with availability date "2026-07-01"
    Then the product status is "SCHEDULED"
    And the product is not yet visible in catalog

  Scenario: Scheduled product becomes active
    Given a product "75423" is scheduled for availability date "2026-07-01"
    When the scheduled date is reached
    Then the product status becomes "PUBLISHED"
    And the product is visible in catalog

Feature: Price Management
  As a sales personnel
  I want to set prices with effective dates
  So that product prices are correct at the right time

  Scenario: Set price with effective date
    Given a product "75423" exists
    When sales sets price 379.99 PLN effective from "2026-07-01"
    Then the price is stored with status "SCHEDULED"

  Scenario: Price activates on schedule
    Given a product "75423" has a scheduled price of 379.99 PLN effective from "2026-07-01"
    When the effective date is reached
    Then the price becomes "ACTIVE"
    And a price changed event is emitted

  Scenario: Price with discount
    Given a product "75423" has active price 379.99 PLN
    When sales adds discount 10% effective from "2026-07-01" to "2026-07-07"
    Then the discounted price is calculated as 341.99 PLN
    And the discount is active during the effective period
```
