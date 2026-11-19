# A microservice

**Stack:** Spring Boot 4 + Java 25 + PostgreSQL + Kafka + OAuth2/Keycloak
**Architecture:** Vertical slices — bounded contexts as sub-packages

Generated from the `microservice-java-spring` blueprint.

## The fulfillment context is split into a kernel and three slices

`structure.shared-kernel-package` (`docs/arch/microservice-java-spring/code-structure.md`): the model every slice only reads is a
root-level, `public` package; each write model and the read model is a root-level, package-private one.

| Package | Owns | Visibility |
| --- | --- | --- |
| `shop.fulfillment.kernel` | the immutable order, parcel, leg, issue and port vocabulary every slice reads | `public` |
| `shop.fulfillment.processing` | `OrderFulfillmentProcess` (aggregate), `OrderProcessing`, `AssignmentGuard`, `ReadyForDeliveryDecision`, `HoldDecision`, `PickListCalculation`, `PickList`, `OrderProcessingRepository` with its document adapter, `OrderIntakeService`, `AssignmentService`, the intake and assignment controllers | package-private |
| `shop.fulfillment.parcel` | `ParcelEditor` (aggregate), `Parcel` | package-private |
| `shop.fulfillment.orders` | `OrdersListProjection` with its read-model store, `OrdersListEntry`, the orders-list controller | package-private |

- Import `kernel` from any slice; import `parcel` from `processing` never, and the reverse never; import a slice from `kernel` never.
- `orders` is a read slice: it is fed by the domain events and imports neither write slice.
- A type two slices need is immutable and belongs in `kernel`.
- A slice's adapters live in that slice's package, so a package-private snapshot stays reachable at the wire.
- The auth matrix of element 11 lives in `AppConfiguration`; every endpoint's decision is recorded in
  `docs/security-decisions.md`.
- `docs/phase-1-decisions.md` records every place phase 1 had to choose or depart from a rule: read it before changing the
  claim's warehouse side, the read model's row shape or the e2e suite.
- Gates: `./gradlew compileJava`, `sqt check .` (holds `structure.slice-package-private` over every slice package) and
  `./gradlew test --tests '*Architecture*'` (`ArchitectureTest`, one rule set per package). `./gradlew e2eHurl` runs the HTTP
  contract of `e2e/fulfillment-api.hurl` against the booted service.
