package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Element 02's consumed edge: the carrier's last leg closes the order, an earlier one changes nothing, and a leg reported for a
 * parcel no processing carries is a fact of another order — ignored, never an error.
 */
class DeliveryStateListenerTest {

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final DeliveryStateListener listener = new DeliveryStateListener(processings);

    @Test
    void closesTheOrderOnTheLastLeg() {
        processings.store(AggregateFixture.aReadyProcessing());

        listener.handle(notified(Leg.L6));

        assertThat(processings.stored(ProcessingFixture.PROCESSING_ID).toSnapshot().state())
                .isEqualTo(ProcessingState.DELIVERED);
        assertThat(processings.stored(ProcessingFixture.PROCESSING_ID).events)
                .last()
                .isInstanceOf(DomainEvent.OrderDelivered.class);
    }

    @Test
    void ignoresEveryEarlierLeg() {
        processings.store(AggregateFixture.aReadyProcessing());
        processings.stored(ProcessingFixture.PROCESSING_ID).events.clear();

        listener.handle(notified(Leg.L3));
        listener.handle(notified(Leg.L5));

        assertThat(processings.stored(ProcessingFixture.PROCESSING_ID).toSnapshot().state())
                .isEqualTo(ProcessingState.READY_FOR_DELIVERY);
        assertThat(processings.saved).isEmpty();
    }

    @Test
    void appliesTheLastLegOnlyOnce() {
        processings.store(AggregateFixture.aReadyProcessing());
        listener.handle(notified(Leg.L6));
        processings.stored(ProcessingFixture.PROCESSING_ID).events.clear();

        listener.handle(notified(Leg.L6));

        assertThat(processings.stored(ProcessingFixture.PROCESSING_ID).events).isEmpty();
        assertThat(processings.saved).hasSize(1);
    }

    @Test
    void ignoresALegOfAParcelNoProcessingCarries() {
        processings.store(AggregateFixture.aReadyProcessing());

        listener.handle(ProcessingFixture.aLegNotified(new ParcelId("PCL-2026-0404"), Leg.L6));

        assertThat(processings.stored(ProcessingFixture.PROCESSING_ID).toSnapshot().state())
                .isEqualTo(ProcessingState.READY_FOR_DELIVERY);
        assertThat(processings.saved).isEmpty();
    }

    private static DomainEvent.DeliveryStateNotified notified(Leg leg) {
        return ProcessingFixture.aLegNotified(ProcessingFixture.PARCEL_ID, leg);
    }
}
