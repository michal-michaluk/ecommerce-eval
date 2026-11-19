package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 02's aggregate: every edge of its state machine is one command, one event, and a state no caller can write; a
 * position never regresses and a hold never moves on a polled signal.
 */
class OrderFulfillmentProcessTest {

    @Test
    void opensWithOnePositionPerLineAndAnnouncesItself() {
        OrderFulfillmentProcess processing = AggregateFixture.pendingProcessingOf(OrderFixture.twoLineOrder());

        OrderProcessing snapshot = processing.toSnapshot();
        assertThat(snapshot.state()).isEqualTo(ProcessingState.PENDING);
        assertThat(snapshot.assignee()).isNull();
        assertThat(snapshot.positions()).hasSize(2)
                .allSatisfy(position -> assertThat(position.state()).isEqualTo(PositionState.PENDING));
        assertThat(processing.events).singleElement().isInstanceOf(DomainEvent.OrderProcessingStarted.class);
    }

    @Test
    void pinsTheOrderItWasStartedFor() {
        OrderFulfillmentProcess processing = AggregateFixture.aPendingProcessing();

        assertThat(processing.toSnapshot().order()).isEqualTo(OrderFixture.anOrder());
        assertThat(processing.toSnapshot().orderHash()).isEqualTo(OrderFixture.ORDER_HASH);
    }

    @Test
    void grantsAFirstClaimAndEmitsOneEvent() {
        OrderFulfillmentProcess processing = AggregateFixture.aPendingProcessing();

        processing.assignTo(ProcessingFixture.EMPLOYEE);

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.ASSIGNED);
        assertThat(processing.toSnapshot().assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        assertThat(processing.events).filteredOn(DomainEvent.EmployeeAssigned.class::isInstance).hasSize(1);
    }

    @Test
    void emitsNothingWhenTheOwnerClaimsAgain() {
        OrderFulfillmentProcess processing = AggregateFixture.anAssignedProcessing();
        int events = processing.events.size();

        processing.assignTo(ProcessingFixture.EMPLOYEE);

        assertThat(processing.events).hasSize(events);
    }

    @Test
    void refusesAForeignClaimWithTheGuardsReason() {
        OrderFulfillmentProcess processing = AggregateFixture.anAssignedProcessing();

        assertThatThrownBy(() -> processing.assignTo(ProcessingFixture.OTHER_EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ASSIGNMENT_REFUSED));
        assertThat(processing.toSnapshot().assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
    }

    @Test
    void theFirstScanStartsPickingAndTheSecondChangesNothing() {
        OrderFulfillmentProcess processing = AggregateFixture.anAssignedProcessing();

        processing.pick(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);
        int events = processing.events.size();
        processing.pick(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.PICKING);
        assertThat(processing.toSnapshot().positions()).singleElement()
                .satisfies(position -> assertThat(position.state()).isEqualTo(PositionState.PICKED));
        assertThat(processing.events).hasSize(events);
    }

    @Test
    void refusesToPickAnOrderNobodyClaims() {
        OrderFulfillmentProcess processing = AggregateFixture.aPendingProcessing();

        assertThatThrownBy(() -> processing.pick(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_NOT_STARTED));
    }

    @Test
    void refusesAPositionTheOrderDoesNotCarry() {
        OrderFulfillmentProcess processing = AggregateFixture.aPickingProcessing();

        assertThatThrownBy(() -> processing.pick("00000", ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.POSITION_UNKNOWN));
    }

    @Test
    void packsAPickedPositionAndPacksTheOrderOnceEveryPositionIs() {
        OrderFulfillmentProcess processing = AggregateFixture.aPickingProcessing();

        processing.pack(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.PACKED);
        assertThat(processing.events).last().isInstanceOf(DomainEvent.PositionPacked.class);
    }

    @Test
    void refusesToPackAPositionThatIsNotPicked() {
        OrderFulfillmentProcess processing = AggregateFixture.anAssignedProcessing();

        assertThatThrownBy(() -> processing.pack(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.POSITION_NOT_PICKED));
    }

    @Test
    void marksAPackedOrderReadyWithItsParcel() {
        OrderFulfillmentProcess processing = AggregateFixture.aPackedProcessing();

        processing.markReady(ProcessingFixture.PARCEL_ID);

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.READY_FOR_DELIVERY);
        assertThat(processing.toSnapshot().parcelId()).isEqualTo(ProcessingFixture.PARCEL_ID);
    }

    @Test
    void refusesReadinessWithoutARegisteredParcel() {
        OrderFulfillmentProcess processing = AggregateFixture.aPackedProcessing();

        assertThatThrownBy(() -> processing.markReady(null))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.READINESS_REFUSED);
                    assertThat(refused.details()).contains(List.of(ReadyForDeliveryDecision.Reason.NO_PARCEL));
                });
    }

    @Test
    void holdsTheWholeOrderOnAnIssueAndRecordsIt() {
        OrderFulfillmentProcess processing = AggregateFixture.aPickingProcessing();

        processing.reportIssue(ProcessingFixture.anIssue());

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.ON_HOLD);
        assertThat(processing.toSnapshot().heldState()).isEqualTo(ProcessingState.PICKING);
        assertThat(processing.toSnapshot().issues()).singleElement()
                .satisfies(issue -> assertThat(issue.isOpen()).isTrue());
    }

    @Test
    void recordsAFurtherIssueWithoutASecondHold() {
        OrderFulfillmentProcess processing = AggregateFixture.aHeldProcessing();
        int holds = processing.events.stream().filter(DomainEvent.HoldPlaced.class::isInstance).toList().size();

        processing.reportIssue(ProcessingFixture.anIssue());

        assertThat(processing.toSnapshot().issues()).hasSize(2);
        assertThat(processing.events).filteredOn(DomainEvent.HoldPlaced.class::isInstance).hasSize(holds);
    }

    @Test
    void refusesAnIssueReportedBySomebodyWhoDoesNotHoldTheOrder() {
        OrderFulfillmentProcess processing = AggregateFixture.aPickingProcessing();

        assertThatThrownBy(() -> processing.reportIssue(ProcessingFixture.anIssueReportedBy(ProcessingFixture.OTHER_EMPLOYEE)))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ISSUE_NOT_REPORTED_BY_ASSIGNEE));
    }

    @Test
    void refusesAnIssueTheOrderDoesNotAccountFor() {
        OrderFulfillmentProcess processing = AggregateFixture.aPickingProcessing();

        assertThatThrownBy(() -> processing.reportIssue(
                        ProcessingFixture.issueOn(ProcessingFixture.OTHER_PROCESSING_ID, ProcessingFixture.ISSUE_ID)))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ISSUE_UNKNOWN));
    }

    @Test
    void resumesToTheStateItWasHeldFromAndClosesTheNamedIssue() {
        OrderFulfillmentProcess processing = AggregateFixture.aHeldProcessing();

        processing.resume(List.of(ProcessingFixture.ISSUE_ID), Instant.parse("2026-11-19T12:00:00Z"));

        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.PICKING);
        assertThat(processing.toSnapshot().heldState()).isNull();
        assertThat(processing.toSnapshot().issues()).singleElement()
                .satisfies(issue -> assertThat(issue.isOpen()).isFalse());
        assertThat(processing.events).last().isInstanceOf(DomainEvent.OrderResumed.class);
    }

    @Test
    void refusesAResumeThatLeavesAnIssueOpen() {
        OrderFulfillmentProcess processing = AggregateFixture.aHeldProcessing();

        assertThatThrownBy(() -> processing.resume(List.of(), Instant.parse("2026-11-19T12:00:00Z")))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ISSUES_REMAIN));
        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.ON_HOLD);
    }

    @Test
    void refusesAResumeThatNamesAnUnknownIssue() {
        OrderFulfillmentProcess processing = AggregateFixture.aHeldProcessing();

        assertThatThrownBy(() -> processing.resume(List.of(new IssueId("ISS-2026-0099")), Instant.EPOCH))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ISSUE_UNKNOWN));
    }

    @Test
    void refusesWorkWhileTheOrderIsHeld() {
        OrderFulfillmentProcess processing = AggregateFixture.aHeldProcessing();

        assertThatThrownBy(() -> processing.pick(OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_IS_ON_HOLD));
    }

    @Test
    void onlyTheLastLegDeliversTheOrder() {
        OrderFulfillmentProcess processing = AggregateFixture.aReadyProcessing();

        processing.applyDeliveryState(Leg.L3);
        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.READY_FOR_DELIVERY);

        processing.applyDeliveryState(Leg.L6);
        assertThat(processing.toSnapshot().state()).isEqualTo(ProcessingState.DELIVERED);
    }

    @Test
    void keepsTheParcelAClaimedOrderCarries() {
        OrderFulfillmentProcess processing = AggregateFixture.aReadyProcessing();

        assertThat(processing.toSnapshot().parcelId()).isEqualTo(ProcessingFixture.PARCEL_ID);
        assertThat(processing.toSnapshot().issues()).isEmpty();
    }

    @Test
    void refusesANullOrder() {
        assertThatThrownBy(() -> OrderFulfillmentProcess.startProcessing(
                new OrderProcessingId("OP-2026-0001"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
