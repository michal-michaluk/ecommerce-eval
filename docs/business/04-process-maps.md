## 4 Process maps

Four maps cover the two value streams of chapter 1. Every step names the operation a caller invokes or the fact the
system records, so a step can be traced to the contract and to the event that carries it. The figures are Mermaid flows
of steps and facts: they are not BPMN-conformant, carry no pools, gateways or message-flow semantics, and state no timing.
<!-- evidence: docs/specs/fulfillment/spec.md:99-110 -->

### 4.1 Order acceptance and claiming

![Order acceptance and claiming.](generated/diagrams/business-4-1-order-acceptance-and-claiming.svg){#fig-business-4-1-order-acceptance-and-claiming width=100%}

| Step | Owner of the step | Named operation or fact |
| --- | --- | --- |
| 1 | checkout | `POST /orders` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/OrderIntakeController.java:28 --> |
| 2 | fulfillment | `OrderProcessingStarted` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:39 --> |
| 3 | warehouse employee | `GET /orders` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/orders/OrdersListController.java:25 --> |
| 4 | warehouse employee | `POST /processing/{processingId}/assignment` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/AssignmentController.java:31 --> |
| 5 | fulfillment | `EmployeeAssigned` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:43 --> |
| 6 | warehouse | reserve, placed inside the claim <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseReservationsIntegration.java:36-45 --> |

### 4.2 Picking and packing

![Picking and packing.](generated/diagrams/business-4-2-picking-and-packing.svg){#fig-business-4-2-picking-and-packing width=100%}

| Step | Owner of the step | Named operation or fact |
| --- | --- | --- |
| 1 | warehouse employee | `GET /processing/{processingId}/pick-list` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListController.java:23 --> |
| 2 | warehouse | the stock reading behind the list <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickListProjection.java:73-83 --> |
| 3 | warehouse employee | `POST /processing/{processingId}/positions/{productNumber}` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:41 --> |
| 4 | carrier | registration on the first scan <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelService.java:83-89 --> |
| 5 | fulfillment | `DeliveryNumberGenerated` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:90 --> |
| 6 | fulfillment | `PositionPicked` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:47 --> |
| 7 | warehouse employee | `POST /processing/{processingId}/packing` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:48 --> |
| 8 | fulfillment | `PositionPacked` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:51 --> |
| 9 | warehouse employee | `GET /processing/{processingId}/pick-workstation` shows the same positions and counters <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/PickWorkstationController.java:24 --> |

### 4.3 Hand-over and delivery tracking

![Hand-over and delivery tracking.](generated/diagrams/business-4-3-hand-over-and-delivery-tracking.svg){#fig-business-4-3-hand-over-and-delivery-tracking width=100%}

| Step | Owner of the step | Named operation or fact |
| --- | --- | --- |
| 1 | warehouse employee | `POST /processing/{processingId}/readiness` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:54 --> |
| 2 | fulfillment | `ReadyForDeliveryConfirmed` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:55 --> |
| 3 | carrier | the collection booking <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:94 --> |
| 4 | warehouse | the goods issue, stored on the reservation row <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/WarehouseGoodsIssueIntegration.java:40-54 --> |
| 5 | carrier | the status read behind every leg <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/CarrierPollingIntegration.java:44-61 --> |
| 6 | fulfillment | `DeliveryStateNotified` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:98 --> |
| 7 | fulfillment | `OrderDelivered`, on the last leg only <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:59 --> |
| 8 | customer | `GET /processing/{processingId}/state` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/customer/CustomerStateController.java:26 --> |
| 9 | warehouse employee | `GET /processing/{processingId}/documents/label` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelLabelController.java:24 --> |

A carrier status we cannot map is not a leg: it is recorded as `DeliveryStatusUnrecognised` with its raw payload, and the
parcel's leg state stays where it was.
<!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:101-102, services/fulfillment/src/main/java/shop/fulfillment/parcel/ParcelEditor.java:152-158 -->

### 4.4 Exception handling and resume

![Exception handling and resume.](generated/diagrams/business-4-4-exception-handling-and-resume.svg){#fig-business-4-4-exception-handling-and-resume width=100%}

| Step | Owner of the step | Named operation or fact |
| --- | --- | --- |
| 1 | warehouse employee | `POST /processing/{processingId}/issues` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:60 --> |
| 2 | fulfillment | `IssueReported` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:77 --> |
| 3 | fulfillment | `HoldPlaced` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:82 --> |
| 4 | issue resolver | `GET /issues` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/issues/IssueDashboardController.java:26 --> |
| 5 | employee or resolver | `POST /processing/{processingId}/hold/resume` <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/processing/ProcessingCommandController.java:68 --> |
| 6 | fulfillment | `OrderResumed`, carrying the state resumed to <!-- evidence: services/fulfillment/src/main/java/shop/fulfillment/kernel/DomainEvent.java:86 --> |

The dashboard offers no action: no resolve endpoint, no resolve command and no issue lifecycle exist, and the closing
timestamp is written by the resume alone.
<!-- evidence: docs/specs/fulfillment/spec.md:82, services/fulfillment/src/main/java/shop/fulfillment/processing/OrderFulfillmentProcess.java:278-285 -->
