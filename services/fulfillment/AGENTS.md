# A microservice

**Stack:** Spring Boot 4 + Java 25 + PostgreSQL + Kafka + OAuth2/Keycloak
**Architecture:** Vertical slices — bounded contexts as sub-packages

Generated from the `microservice-java-spring` blueprint.

## The fulfillment context is split into a kernel and two slices

`structure.shared-kernel-package` (`docs/arch/microservice-java-spring/code-structure.md`): the model both slices only read is a
root-level, `public` package; each write model is a root-level, package-private one.

| Package | Owns | Visibility |
| --- | --- | --- |
| `shop.fulfillment.kernel` | the immutable order, parcel, leg, issue and port vocabulary both slices read | `public` |
| `shop.fulfillment.processing` | `OrderFulfillmentProcess` (aggregate), `OrderProcessing`, `AssignmentGuard`, `ReadyForDeliveryDecision`, `HoldDecision`, `PickListCalculation`, `PickList` | package-private |
| `shop.fulfillment.parcel` | `ParcelEditor` (aggregate), `Parcel` | package-private |

- Import `kernel` from either slice; import `parcel` from `processing` never, and the reverse never; import a slice from `kernel` never.
- A type both slices need is immutable and belongs in `kernel`.
- A slice's adapters live in that slice's package, so a package-private snapshot stays reachable at the wire.
- Gates: `./gradlew compileJava`, `sqt check .` (holds `structure.slice-package-private` over both slice packages) and
  `./gradlew test --tests '*Architecture*'` (`ArchitectureTest`, one rule set per package).
