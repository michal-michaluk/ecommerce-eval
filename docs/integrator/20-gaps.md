## 20 Gaps

Each entry names one thing this document asserts is absent, and what a client meets instead.

1. **GAP-1 — the collections are unpaginated.** `GET /orders` and `GET /issues` take no parameter and answer one
   `{ "items": [ … ] }` envelope; there is no `page`, no `size`, no cursor, no `Link` header and no total. No volume or
   latency figure exists anywhere in the sources, so no page size and no default could be derived, and a client receives
   the whole collection on every call.
   <!-- gap: GAP-1 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListController.java:15, services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardController.java:18, docs/specs/fulfillment/11-fulfillment-http-api.md:124, docs/decisions/operator-input.md:13 -->

2. **GAP-2 — the customer state view checks no ownership.** `fulfillment-customer` alone reaches
   `GET /processing/{processingId}/state`, and the handler compares no identity against the id in the path: the order
   carries no customer identity to compare with. Any token with that scope reads any processing's state view.
   <!-- gap: GAP-2 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:17, docs/specs/fulfillment/spec.md:87, docs/decisions/operator-input.md:12 -->

3. **GAP-3 — no rate limit, no quota, no `429`.** The security chain registers authentication and authorisation and nothing
   else, the configuration declares no rate or quota property, and the contract asserts no `429`, no `Retry-After` and no
   rate header. A burst of retries is answered as fast as the database answers them.
   <!-- gap: GAP-3 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:58, services/fulfillment/src/main/resources/application.yml:1, services/fulfillment/e2e/fulfillment-api.hurl:1 -->

4. **GAP-4 — no version marker and no content negotiation for JSON.** No route carries a version segment or header, and
   every JSON handler declares `produces = application/json` without reading `Accept`; the only representation choice in
   the API is the label's `application/pdf`. A breaking change has no marker to hang on, so it would land as the same
   contract.
   <!-- gap: GAP-4 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:28, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24, docs/specs/fulfillment/11-fulfillment-http-api.md:22 -->

5. **GAP-5 — `400` is outside the contract's vocabulary.** No advice handles a bean-validation failure or an unreadable body,
   so those answers come from the framework with its own error document and no `{ "error": "<CODE>" }` of this contract. The
   bodies that can trigger it are constrained: `confirmation` must match `picked`, `productNumbers` must not be empty, `kind`
   and `productNumber` must not be blank, `closedIssueIds` must be present. No request body carries an enum, so a malformed
   enum is not the trigger — a violated constraint is.
   <!-- gap: GAP-5 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:29, services/fulfillment/src/main/java/shop/fulfillment/processing/ScanPosition.java:13, services/fulfillment/src/main/java/shop/fulfillment/processing/PackPositions.java:13, services/fulfillment/src/main/resources/application.yml:26 -->

6. **GAP-6 — no client-supplied idempotency key.** No request carries a key, and the service stores none. A repeat is
   recognised by the order's canonical digest at intake, or by the state the command lands on; a report of an issue can
   therefore never be made repeatable, and a client that retries `POST …/issues` creates a second issue.
   <!-- gap: GAP-6 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeService.java:16, services/fulfillment/src/main/java/shop/fulfillment/processing/ReportIssue.java:13, docs/specs/fulfillment/11-fulfillment-http-api.md:118 -->

7. **GAP-7 — `500` carries no contract code.** Every failure the advice does not name is rethrown, so an unwired warehouse or
   carrier, a malformed domain value and an unexpected fault all answer `500` with the framework's document. A client cannot
   tell a deployment that has no warehouse adapter from a crash, and both read the same on the wire.
   <!-- gap: GAP-7 -->
   <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:71, services/fulfillment/src/main/java/shop/fulfillment/kernel/FulfillmentException.java:19, services/fulfillment/src/main/resources/application.yml:30 -->

8. **GAP-8 — the service serves no machine-readable description of itself.** No OpenAPI document is produced or exposed at
   runtime; the contract exists as the hurl file and as the docset's dataset, both built from the source tree. A client
   cannot fetch a schema from the running service.
   <!-- gap: GAP-8 -->
   <!-- evidence: services/fulfillment/build.gradle:24, docs/scripts/config.json:17, services/fulfillment/e2e/fulfillment-api.hurl:1 -->

9. **GAP-9 — no assignment release.** No route releases a claim and no scope is granted one, so a claim ends only when the
   order reaches `readyForDelivery` or `delivered`. Whether a resolver may release an order, and what a picked position
   becomes afterwards, stays open.
   <!-- gap: GAP-9 -->
   <!-- evidence: docs/specs/fulfillment/11-fulfillment-http-api.md:156, services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:85 -->

10. **GAP-10 — the label document has no cache validator.** `GET …/documents/label` answers bytes with no `ETag`, no
    `Last-Modified` and no `Cache-Control`, although the bytes are stored on the first print and served back unchanged on
    every reprint. A reprinting client cannot ask whether the document it holds is still current and re-downloads it.
    <!-- gap: GAP-10 -->
    <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelProjection.java:53 -->

11. **GAP-11 — `missing[]` cannot always be resolved to the part to fix.** A duplicated product line is answered `422
    ORDER_INCOMPLETE` with `missing: ["lines.productNumber"]` — the same path an absent product number produces, for a
    different cause — and an absent address component is named `address.street` without saying whether it is the delivery
    address or the invoicing address. A client that branches on `missing[]` sees two causes under one path and one path
    under two objects.
    <!-- gap: GAP-11 -->
    <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/FulfillmentErrorAdvice.java:78, services/fulfillment/src/main/java/shop/fulfillment/kernel/Address.java:19, services/fulfillment/src/main/java/shop/fulfillment/kernel/Order.java:144 -->

12. **GAP-12 — a `201` carries no `Location`.** The handover, the claim and the issue report answer `201` with a body and no
    `Location` header, so a client learns the new identity from the payload only, and no route states where the created
    resource is readable from.
    <!-- gap: GAP-12 -->
    <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:31, services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentController.java:36, services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:64 -->

13. **GAP-13 — no CORS policy.** No CORS configuration exists in the service, so a browser-based customer or warehouse
    surface on another origin cannot call the API directly; nothing in source states whether that is intended.
    <!-- gap: GAP-13 -->
    <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/AppConfiguration.java:51, services/fulfillment/src/main/resources/application.yml:1 -->
