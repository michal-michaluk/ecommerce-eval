package shop.fulfillment.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Element 15's read model: one row per current order, find-or-create on every event, and the state recomputed from the facts
 * the row holds — so the same events in the other order end in the same row.
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

    private static DomainEvent.OrderProcessingStarted started(OrderProcessingId processingId,
                                                              shop.fulfillment.kernel.Order order) {
        return new DomainEvent.OrderProcessingStarted(processingId, order);
    }

    private static DomainEvent.EmployeeAssigned assigned(OrderProcessingId processingId, String assignee) {
        return new DomainEvent.EmployeeAssigned(processingId, assignee);
    }
}
