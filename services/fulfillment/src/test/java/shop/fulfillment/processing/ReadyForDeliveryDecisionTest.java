package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The readiness table of element 08, in the order the element evaluates it: a parcel is required before anything else, a hold
 * blocks the mark, every open position blocks it, and a replay of a grant changes nothing.
 */
class ReadyForDeliveryDecisionTest {

    @Test
    void refusesEverythingWithoutARegisteredParcel() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.PACKED, true, false))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.NO_PARCEL));
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.DELIVERED, true, false))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.NO_PARCEL));
    }

    @Test
    void refusesAReadyMarkOnAHeldOrder() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.ON_HOLD, true, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.ON_HOLD));
    }

    @Test
    void refusesAReadyMarkWhilePositionsAreStillOpen() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.PICKING, true, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.NOT_PACKED));
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.PACKED, false, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.POSITIONS_OPEN));
    }

    @Test
    void grantsAReadyMarkOnAPackedOrderWithItsParcel() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.PACKED, true, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Grant());
    }

    @Test
    void answersAReplayAsANoOp() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.READY_FOR_DELIVERY, true, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.NoOp());
    }

    @Test
    void refusesAReadyMarkOnADeliveredOrder() {
        assertThat(ReadyForDeliveryDecision.decide(ProcessingState.DELIVERED, true, true))
                .isEqualTo(new ReadyForDeliveryDecision.Verdict.Refuse(ReadyForDeliveryDecision.Reason.PAST_HANDOVER));
    }
}
