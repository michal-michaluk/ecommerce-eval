package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import shop.catalog.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;
import static shop.catalog.pricing.PricingFixture.publishedOffer;

/**
 * The one fact pricing keeps about the offer context (17 RULE-4): the listener creates the row it has never seen, a
 * re-publication of the same offer rewrites that row instead of adding a second one, and the snapshot the offer context
 * publishes is what actually reaches it — the handler is called here through the application events, not by hand.
 */
@IntegrationTest
@TestPropertySource(properties = "catalog.pricing.activation-interval=PT24H")
class KnownOfferListenerTest {

    @Autowired
    KnownOfferListener listener;
    @Autowired
    KnownOfferListener.KnownOffers knownOffers;
    @Autowired
    ApplicationEventPublisher events;

    @Test
    void recordsThatAnOfferItHasNeverSeenExists() {
        listener.handle(publishedOffer(OFFER_ID));

        assertThat(knownOffers.findById(OFFER_ID)).isPresent();
    }

    @Test
    void recordsTheSameFactOnceWhenItArrivesTwice() {
        listener.handle(publishedOffer(OFFER_ID));
        listener.handle(publishedOffer(OFFER_ID));

        assertThat(knownOffers.findAll()).hasSize(1);
    }

    @Test
    void recordsTheFactFromAPublishedOfferSnapshot() {
        events.publishEvent(publishedOffer(OFFER_ID));

        assertThat(knownOffers.findById(OFFER_ID)).isPresent();
    }
}
