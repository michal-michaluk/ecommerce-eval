package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static shop.catalog.pricing.ActivationFixture.OTHER_OFFER_ID;
import static shop.catalog.pricing.ActivationFixture.POLL_INSTANT;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;

/**
 * The trigger's own contract, with the port faked and a clock that ticks once per read: the poll is one instant for
 * the whole tick (RULE-8) and one call per offering the port answered, never a clock read per offer.
 */
class PricingActivationPollTest {

    final PricingService pricing = mock(PricingService.class);
    final NormalizingPricingRepository awaiting = mock(NormalizingPricingRepository.class);
    final PricingActivationListener poll = ActivationFixture.poll(pricing, awaiting, new TickingClock());

    @Test
    void asksThePrimaryPortToActivateEveryAwaitingOffer() {
        when(awaiting.offerIdsAwaitingActivation()).thenReturn(List.of(OFFER_ID, OTHER_OFFER_ID));

        poll.activateDueEntries();

        verify(pricing).activateDue(eq(OFFER_ID), any());
        verify(pricing).activateDue(eq(OTHER_OFFER_ID), any());
    }

    @Test
    void takesOneInstantForTheWholeTick() {
        when(awaiting.offerIdsAwaitingActivation()).thenReturn(List.of(OFFER_ID, OTHER_OFFER_ID));
        ArgumentCaptor<Instant> instant = ArgumentCaptor.forClass(Instant.class);

        poll.activateDueEntries();

        verify(pricing, times(2)).activateDue(anyString(), instant.capture());
        assertThat(instant.getAllValues()).containsOnly(instant.getValue());
    }

    @Test
    void asksNothingWhenNoOfferIsWaiting() {
        when(awaiting.offerIdsAwaitingActivation()).thenReturn(List.of());

        poll.activateDueEntries();

        verify(pricing, times(0)).activateDue(anyString(), any());
    }

    /** A clock that advances one second per read, so a second read inside one tick is visible. */
    static class TickingClock extends Clock {

        private int reads;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return POLL_INSTANT.plusSeconds(reads++);
        }
    }
}
