package shop.fulfillment.customer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.JsonAssert;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 18's read model fed by the events of the lane: one row per processing with the state in the contract's own words, the
 * delivery legs oldest first, and neither an assignee nor an issue anywhere in the payload — because neither is a column.
 */
@IntegrationTest
class CustomerStateProjectionTest {

    static final OrderProcessingId PROCESSING_ID = ProcessingFixture.PROCESSING_ID;

    @Autowired
    CustomerStateProjection projection;
    @Autowired
    CustomerStateProjection.CustomerStates states;
    @Autowired
    CustomerStateProjection.CustomerLegs legs;

    @BeforeEach
    void forgetsEveryRow() {
        legs.deleteAll();
        states.deleteAll();
    }

    @Test
    void startsTheViewFromTheHandover() {
        projection.handle(CustomerFixture.started(PROCESSING_ID));

        JsonAssert.assertThat(projection.findState(PROCESSING_ID.value())).isExactlyLike("""
                {"state": "pending",
                 "lines": [{"productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1}],
                 "delivery": {"methodCode": "PARCEL_LOCKER",
                              "location": {"kind": "PICKUP_POINT", "pointId": "KRA01M", "address": null}},
                 "legs": []}""");
    }

    @Test
    void followsTheProcessingsOwnStates() {
        projection.handle(CustomerFixture.started(PROCESSING_ID));
        projection.handle(CustomerFixture.assigned(PROCESSING_ID));
        projection.handle(CustomerFixture.picked(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("picking");

        projection.handle(CustomerFixture.packed(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("packed");
    }

    @Test
    void countsAPositionOnceHoweverOftenTheBoxIsReported() {
        projection.handle(CustomerFixture.started(PROCESSING_ID));
        projection.handle(CustomerFixture.assigned(PROCESSING_ID));
        projection.handle(CustomerFixture.picked(PROCESSING_ID));
        projection.handle(CustomerFixture.packed(PROCESSING_ID));

        projection.handle(CustomerFixture.packed(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("packed");
    }

    @Test
    void showsAHoldAsTheStateAndNothingElse() {
        projection.handle(CustomerFixture.started(PROCESSING_ID));
        projection.handle(CustomerFixture.picked(PROCESSING_ID));
        projection.handle(CustomerFixture.held(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("onHold");

        projection.handle(CustomerFixture.resumed(PROCESSING_ID, ProcessingState.PICKING));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("picking");
    }

    @Test
    void servesTheDeliveryTimelineOldestFirstAndNamesNoAssigneeOrIssue() {
        givenAPackedOrder();

        projection.handle(CustomerFixture.ready(PROCESSING_ID));
        projection.handle(CustomerFixture.notified(Leg.L3, "ASSIGNED_FOR_PICKUP"));
        projection.handle(CustomerFixture.notified(Leg.L4, "PICKED_UP"));

        CustomerState state = projection.findState(PROCESSING_ID.value());

        assertThat(state.state()).isEqualTo("readyForDelivery");
        assertThat(state.legs()).extracting(CustomerState.Step::leg).containsExactly("L1", "L2", "L3", "L4");
        assertThat(JsonAssert.json(state)).doesNotContain("assignee").doesNotContain("issue")
                .doesNotContain("price").doesNotContain("amount");
    }

    @Test
    void keepsOneStepPerLegHoweverOftenItIsReported() {
        givenAPackedOrder();
        projection.handle(CustomerFixture.ready(PROCESSING_ID));
        DomainEvent.DeliveryStateNotified notified = CustomerFixture.notified(Leg.L3, "ASSIGNED_FOR_PICKUP");
        projection.handle(notified);
        CustomerState first = projection.findState(PROCESSING_ID.value());

        projection.handle(notified);

        assertThat(projection.findState(PROCESSING_ID.value()).legs()).isEqualTo(first.legs());
    }

    @Test
    void movesToDeliveredAndNeverBackwards() {
        givenAPackedOrder();
        projection.handle(CustomerFixture.ready(PROCESSING_ID));

        projection.handle(CustomerFixture.delivered(PROCESSING_ID));
        projection.handle(CustomerFixture.packed(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("delivered");
    }

    @Test
    void recomputesTheSameRowWhateverOrderTheEventsArriveIn() {
        projection.handle(CustomerFixture.notified(Leg.L5, "IN_SORTING_FACILITY"));
        projection.handle(CustomerFixture.packed(PROCESSING_ID));
        projection.handle(CustomerFixture.picked(PROCESSING_ID));
        projection.handle(CustomerFixture.ready(PROCESSING_ID));
        projection.handle(CustomerFixture.started(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).state()).isEqualTo("readyForDelivery");
        assertThat(projection.findState(PROCESSING_ID.value()).legs()).extracting(CustomerState.Step::leg)
                .containsExactly("L5");
    }

    @Test
    void answersAnUnknownProcessingWithTheContractsRefusal() {
        assertThatThrownBy(() -> projection.findState("OP-2026-9999"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
    }

    @Test
    void recordsTheMomentWeHeardOfEachLeg() {
        givenAPackedOrder();
        projection.handle(CustomerFixture.ready(PROCESSING_ID));

        assertThat(projection.findState(PROCESSING_ID.value()).legs())
                .hasSize(2)
                .allSatisfy(step -> assertThat(step.at()).isNotNull());
    }

    private void givenAPackedOrder() {
        projection.handle(CustomerFixture.started(PROCESSING_ID));
        projection.handle(CustomerFixture.assigned(PROCESSING_ID));
        projection.handle(CustomerFixture.picked(PROCESSING_ID));
        projection.handle(CustomerFixture.packed(PROCESSING_ID));
        projection.handle(CustomerFixture.numberGenerated());
        projection.handle(CustomerFixture.collectionRequested());
    }
}
