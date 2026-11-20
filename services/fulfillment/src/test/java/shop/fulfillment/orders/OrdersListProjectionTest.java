package shop.fulfillment.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Element 15's read model: one row per current order, find-or-create on every event, and the state recomputed from the facts
 * the row holds — so the same events in the other order end in the same row, and a fact the list receives twice is the fact
 * it already had.
 */
@IntegrationTest
class OrdersListProjectionTest {

    @Autowired
    OrdersListProjection projection;

    @Test
    void startsARowFromTheHandover() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.processingId()).isEqualTo(ProcessingFixture.PROCESSING_ID.value());
            assertThat(row.state()).isEqualTo("pending");
            assertThat(row.recipient()).isEqualTo("Jan Kowalski");
            assertThat(row.positionProgress()).isEqualTo("0/1 packed");
            assertThat(row.onHold()).isFalse();
        });
    }

    @Test
    void showsWhoHoldsTheOrderOnceItIsClaimed() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("assigned");
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        });
    }

    @Test
    void movesTheChipToPickingWhenTheFirstPositionIsScanned() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.twoLineOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        projection.handle(picked(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("picking");
            assertThat(row.positionProgress()).isEqualTo("0/2 packed");
        });
    }

    @Test
    void countsThePositionsInTheBoxAndSaysPackedOnceEveryOneIs() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.twoLineOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(picked(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        projection.handle(picked(ProcessingFixture.PROCESSING_ID, "99881"));

        projection.handle(packed(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.positionProgress()).isEqualTo("1/2 packed");
            assertThat(row.state()).isEqualTo("picking");
        });

        projection.handle(packed(ProcessingFixture.PROCESSING_ID, "99881"));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.positionProgress()).isEqualTo("2/2 packed");
            assertThat(row.state()).isEqualTo("packed");
        });
    }

    @Test
    void countsAProductOnceHoweverOftenItIsPackedAgain() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.twoLineOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(picked(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));

        projection.handle(packed(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        projection.handle(packed(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.positionProgress()).isEqualTo("1/2 packed");
            assertThat(row.state()).isEqualTo("picking");
        });
    }

    @Test
    void movesTheChipToReadyForDeliveryAndThenToDelivered() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(picked(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        projection.handle(packed(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));

        projection.handle(readyForDelivery(ProcessingFixture.PROCESSING_ID));
        assertThat(projection.findAllCurrent()).singleElement()
                .satisfies(row -> assertThat(row.state()).isEqualTo("readyForDelivery"));

        projection.handle(delivered(ProcessingFixture.PROCESSING_ID));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("delivered");
            assertThat(row.onHold()).isFalse();
        });
    }

    @Test
    void keepsOneRowForOneProcessing() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        assertThat(projection.findAllCurrent()).hasSize(1);
    }

    @Test
    void recomputesTheSameRowWhateverOrderTheEventsArriveIn() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("assigned");
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        });
    }

    @Test
    void showsNothingUntilTheHandoverBringsTheRowItsRecipientAndPlacementTime() {
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        assertThat(projection.findAllCurrent()).isEmpty();

        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
            assertThat(row.recipient()).isEqualTo("Jan Kowalski");
        });
    }

    @Test
    void listsTheCurrentOrdersOldestFirst() {
        projection.handle(started(ProcessingFixture.OTHER_PROCESSING_ID, OrderFixture.anOrder("sha256:9ab27e")));
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));

        assertThat(projection.findAllCurrent()).extracting(OrdersListEntry::processingId)
                .containsExactly(ProcessingFixture.PROCESSING_ID.value(),
                        ProcessingFixture.OTHER_PROCESSING_ID.value());
    }

    @Test
    void countsThePositionsOfAMultiPositionOrder() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.twoLineOrder()));

        assertThat(projection.findAllCurrent()).singleElement()
                .satisfies(row -> assertThat(row.positionProgress()).isEqualTo("0/2 packed"));
    }

    @Test
    void marksTheWholeOrderOnHoldAndSaysSo() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("onHold");
            assertThat(row.onHold()).isTrue();
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        });
    }

    @Test
    void showsTheStateTheLiftRestoredAndDropsTheHoldMarker() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PICKING));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("picking");
            assertThat(row.onHold()).isFalse();
        });
    }

    @Test
    void keepsMovingAfterTheLiftInsteadOfStandingOnTheRestoredState() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.twoLineOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PICKING));

        projection.handle(picked(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        projection.handle(picked(ProcessingFixture.PROCESSING_ID, "99881"));
        projection.handle(packed(ProcessingFixture.PROCESSING_ID, OrderFixture.PRODUCT_NUMBER));
        projection.handle(packed(ProcessingFixture.PROCESSING_ID, "99881"));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("packed");
            assertThat(row.positionProgress()).isEqualTo("2/2 packed");
        });
    }

    @Test
    void reachesReadinessAfterTheLiftInsteadOfStandingOnTheRestoredState() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PACKED));

        projection.handle(readyForDelivery(ProcessingFixture.PROCESSING_ID));

        assertThat(projection.findAllCurrent()).singleElement()
                .satisfies(row -> assertThat(row.state()).isEqualTo("readyForDelivery"));
    }

    @Test
    void keepsTheHoldWhateverElseIsReplayedAfterwards() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        assertThat(projection.findAllCurrent()).singleElement().satisfies(row -> {
            assertThat(row.state()).isEqualTo("onHold");
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        });
    }

    private static DomainEvent.HoldPlaced held(OrderProcessingId processingId) {
        return new DomainEvent.HoldPlaced(processingId, ProcessingFixture.REPORTED_AT);
    }

    private static DomainEvent.OrderResumed resumed(OrderProcessingId processingId, ProcessingState state) {
        return new DomainEvent.OrderResumed(processingId, state);
    }

    private static DomainEvent.OrderProcessingStarted started(OrderProcessingId processingId,
                                                              shop.fulfillment.kernel.Order order) {
        return new DomainEvent.OrderProcessingStarted(processingId, order);
    }

    private static DomainEvent.EmployeeAssigned assigned(OrderProcessingId processingId, String assignee) {
        return new DomainEvent.EmployeeAssigned(processingId, assignee);
    }

    private static DomainEvent.PositionPicked picked(OrderProcessingId processingId, String productNumber) {
        return new DomainEvent.PositionPicked(processingId, productNumber);
    }

    private static DomainEvent.PositionPacked packed(OrderProcessingId processingId, String productNumber) {
        return new DomainEvent.PositionPacked(processingId, productNumber);
    }

    private static DomainEvent.ReadyForDeliveryConfirmed readyForDelivery(OrderProcessingId processingId) {
        return new DomainEvent.ReadyForDeliveryConfirmed(processingId, ProcessingFixture.PARCEL_ID);
    }

    private static DomainEvent.OrderDelivered delivered(OrderProcessingId processingId) {
        return new DomainEvent.OrderDelivered(processingId);
    }
}
