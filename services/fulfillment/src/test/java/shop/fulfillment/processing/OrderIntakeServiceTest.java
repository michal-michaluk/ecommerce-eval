package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 12 RULE-1…RULE-4: one handover opens one pending processing and announces it, a repeated {@code orderHash} answers
 * the processing that already exists without a second write or a second event, another order under a used {@code orderHash} is
 * refused as a contract error, and an incomplete order stores nothing.
 */
class OrderIntakeServiceTest {

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final OrderIntakeService orders = new OrderIntakeService(processings);

    @BeforeEach
    void nothingStoredYet() {
        processings.processings.clear();
        processings.saved.clear();
    }

    @Test
    void opensAPendingProcessingForAFinishedOrder() {
        OrderAcceptance acceptance = orders.accept(OrderHandoverFixture.aHandover());

        assertThat(acceptance.duplicate()).isFalse();
        assertThat(acceptance.processing().state().wireName()).isEqualTo("pending");
        assertThat(acceptance.processing().processingId().value()).isEqualTo("OP-2026-0001");
    }

    @Test
    void createsOnePositionPerOrderLine() {
        OrderAcceptance acceptance = orders.accept(OrderHandoverFixture.aTwoLineHandover());

        assertThat(acceptance.processing().positions()).hasSize(2)
                .allSatisfy(position -> assertThat(position.state()).isEqualTo(shop.fulfillment.kernel.PositionState.PENDING));
    }

    @Test
    void announcesTheStartOfTheProcessingOnce() {
        orders.accept(OrderHandoverFixture.aHandover());

        assertThat(processings.all()).singleElement()
                .satisfies(processing -> assertThat(processing.events)
                        .singleElement().isInstanceOf(DomainEvent.OrderProcessingStarted.class));
    }

    @Test
    void answersTheSameProcessingForARepeatedOrderHash() {
        OrderAcceptance first = orders.accept(OrderHandoverFixture.aHandover());

        OrderAcceptance second = orders.accept(OrderHandoverFixture.aHandover());

        assertThat(second.duplicate()).isTrue();
        assertThat(second.processing().processingId()).isEqualTo(first.processing().processingId());
    }

    @Test
    void storesNothingAndAnnouncesNothingForARepeatedOrderHash() {
        orders.accept(OrderHandoverFixture.aHandover());

        orders.accept(OrderHandoverFixture.aHandover());

        assertThat(processings.saved).hasSize(1);
        assertThat(processings.all()).singleElement()
                .satisfies(processing -> assertThat(processing.events).hasSize(1));
    }

    @Test
    void refusesASecondOrderSentUnderAUsedOrderHash() {
        orders.accept(OrderHandoverFixture.aHandover());

        assertThatThrownBy(() -> orders.accept(OrderHandoverFixture.aTwoLineHandover()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ORDER_HASH_CONFLICT);
                    assertThat(refused.details()).containsExactly(OrderFixture.ORDER_HASH);
                });
    }

    @Test
    void storesNothingAndAnnouncesNothingForAConflictingOrderHash() {
        orders.accept(OrderHandoverFixture.aHandover());

        assertThatThrownBy(() -> orders.accept(OrderHandoverFixture.aTwoLineHandover()))
                .isInstanceOf(FulfillmentException.class);

        assertThat(processings.saved).hasSize(1);
        assertThat(processings.all()).singleElement()
                .satisfies(processing -> assertThat(processing.events).hasSize(1));
    }

    @Test
    void treatsAnotherOrderAsANewProcessing() {
        orders.accept(OrderHandoverFixture.aHandover());

        OrderAcceptance other = orders.accept(OrderHandoverFixture.aHandover("sha256:9ab27e"));

        assertThat(other.duplicate()).isFalse();
        assertThat(other.processing().processingId().value()).isEqualTo("OP-2026-0002");
    }

    @Test
    void refusesAnIncompleteOrderAndStoresNothing() {
        assertThatThrownBy(() -> orders.accept(OrderHandoverFixture.anIncompleteHandover()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ORDER_INCOMPLETE));
        assertThat(processings.all()).isEmpty();
    }
}
