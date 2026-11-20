## 9 Domain events

<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:39 -->
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:98 -->

Every event is a nested record of one sealed interface (`kernel/DomainEvent.java`), named in the past
tense and carrying the identity of the thing that changed. There are thirteen.

| Event | Raised by | Consumed by | Crosses a boundary |
| --- | --- | --- | --- |
| `OrderProcessingStarted` (`:39`) | intake | orders list, dashboard, customer state | no |
| `EmployeeAssigned` (`:43`) | granted claim | orders list, dashboard, customer state | no |
| `PositionPicked` (`:47`) | scan confirmation | orders list, customer state | no |
| `PositionPacked` (`:51`) | packing | orders list, customer state | no |
| `ReadyForDeliveryConfirmed` (`:55`) | readiness, after both effects | orders list, customer state | no |
| `OrderDelivered` (`:59`) | last delivery leg | orders list, customer state | no |
| `StockChanged` (`:68`) | stock poller | nothing yet — no projection subscribes | no |
| `IssueReported` (`:77`) | issue report | issue dashboard | no |
| `HoldPlaced` (`:82`) | issue report that starts a hold | orders list, dashboard, customer state | no |
| `OrderResumed` (`:86`) | granted resume | orders list, dashboard, customer state | no |
| `DeliveryNumberGenerated` (`:90`) | parcel registration | customer state | no |
| `CollectionRequested` (`:94`) | granted readiness | customer state | no |
| `DeliveryStateNotified` (`:98`) | carrier poll | the process (moves it to delivered on the last leg), customer state | no |
| `DeliveryStatusUnrecognised` (`:101`) | carrier poll | nothing — it exists so the raw payload is observable | no |

Two rules hold across the table. A refused command raises nothing: `AssignmentGuard`, `HoldDecision` and
`ReadyForDeliveryDecision` return a verdict and the aggregate throws before any event is appended. A
replayed command raises nothing either: a repeated scan, pack or handover returns the current state and
appends no second event. `StockChanged` is the only event with no subscriber — the pick list is computed
on read rather than projected (`GAP-10`).

Because publication is in-process only, "consumed by" always means a `@EventListener` in this same
service. No Kafka topic is declared, no broker connection is configured, and the `spring.kafka.consumer`
block in `application.yml` is inherited from the scaffold and unused.
