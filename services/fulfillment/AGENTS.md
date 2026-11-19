# A microservice

**Stack:** Spring Boot 4 + Java 25 + PostgreSQL + Kafka + OAuth2/Keycloak
**Architecture:** Vertical slices — bounded contexts as sub-packages

Generated from the `microservice-java-spring` blueprint.

## The fulfillment context is split into a kernel and four slices

`structure.shared-kernel-package` (`docs/arch/microservice-java-spring/code-structure.md`): the model every slice only reads is a
root-level, `public` package; each write model and the read model is a root-level, package-private one.

| Package | Owns | Visibility |
| --- | --- | --- |
| `shop.fulfillment.kernel` | the immutable order, parcel, leg, issue and port vocabulary every slice reads — including `Parcels`, the contract port the processing slice holds and the parcel slice implements | `public` |
| `shop.fulfillment.processing` | `OrderFulfillmentProcess` (aggregate), `OrderProcessing`, `AssignmentGuard`, `ReadyForDeliveryDecision`, `HoldDecision`, `PickListCalculation`, `PickList`, `OrderProcessingRepository` with its document adapter, `OrderIntakeService`, `AssignmentService`, `ProcessingService`, `DeliveryStateListener`, the intake, assignment and processing controllers | package-private |
| `shop.fulfillment.parcel` | `ParcelEditor` (aggregate), `Parcel`, `ParcelRepository` with its document adapter, `ParcelService` (the `Parcels` contract), `CarrierPollingIntegration`, the carrier adapter, the label projection and controller | package-private |
| `shop.fulfillment.orders` | `OrdersListProjection` with its read-model store, `OrdersListEntry`, the orders-list controller | package-private |
| `shop.fulfillment.customer` | `CustomerStateProjection` with its read-model store, `CustomerState`, the customer state controller | package-private |
- Import `kernel` from any slice; import `parcel` from `processing` never, and the reverse never; import a slice from `kernel` never.
- `orders` and `customer` are read slices: they are fed by the domain events and import neither write slice.
- A cross-slice need the kernel does not already carry is a **contract port in `kernel`**, implemented by the slice that owns the
  value (`Parcels` is the one) — the two write slices never see one another's types (recorded as `P3-1` in
  `docs/phase-1-decisions.md`).
- A type two slices need is immutable and belongs in `kernel`.
- A slice's adapters live in that slice's package, so a package-private snapshot stays reachable at the wire.
- The auth matrix of element 11 lives in `AppConfiguration`; every endpoint's decision is recorded in
  `docs/security-decisions.md`.
- `docs/phase-1-decisions.md` records every place a phase had to choose or depart from a rule: read it before changing the
  claim's warehouse side, the parcel's registration, the readiness command, a read model's row shape or the e2e suite.
- Gates: `./gradlew compileJava`, `sqt check .` (holds `structure.slice-package-private` over every slice package) and
  `./gradlew test --tests '*Architecture*'` (`ArchitectureTest`, one rule set per package). `./gradlew e2eHurl` runs the HTTP
  contract of `e2e/fulfillment-api.hurl` against the booted service.
