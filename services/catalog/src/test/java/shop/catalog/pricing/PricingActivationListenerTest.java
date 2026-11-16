package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import shop.catalog.IntegrationTest;
import shop.catalog.RecordedEvents;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.TestTransaction.transactional;
import static shop.catalog.pricing.ActivationFixture.OTHER_OFFER_ID;
import static shop.catalog.pricing.ActivationFixture.POLL_INSTANT;
import static shop.catalog.pricing.ActivationFixture.discountDueLongAgo;
import static shop.catalog.pricing.ActivationFixture.historicPriceDueLongAgo;
import static shop.catalog.pricing.ActivationFixture.priceAndDiscountDueLongAgo;
import static shop.catalog.pricing.ActivationFixture.priceDueInTheFuture;
import static shop.catalog.pricing.ActivationFixture.priceDueLongAgo;
import static shop.catalog.pricing.PricingEventFixture.discountActivated;
import static shop.catalog.pricing.PricingEventFixture.priceActivated;
import static shop.catalog.pricing.PricingFixture.JANUARY_1;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;
import static shop.catalog.pricing.PricingFixture.pln;

/**
 * The RULE-9 poll against a real database, on a fixed clock: every poll takes one instant, so a poll that catches up
 * reports {@code occurredAt} = the poll and {@code effectiveFrom} = the entry's own {@code validFrom} (13 RULE-8), and
 * the marker and the fact commit together — which is what makes a restarted poll emit nothing.
 *
 * <p>The interval is stretched and the clock is frozen so the timer can neither fire by itself mid-test nor depend on
 * the machine date.
 */
@IntegrationTest
@TestPropertySource(properties = "catalog.pricing.activation-interval=PT24H")
class PricingActivationListenerTest {

    @Autowired
    PricingActivationListener activation;
    @Autowired
    NormalizingPricingRepository repository;
    @Autowired
    RecordedEvents recorded;

    @Test
    void catchesUpAfterDowntimeWithThePollInstantAndTheEntrysEffectiveFrom() {
        transactional(() -> repository.save(priceDueLongAgo()));

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.PriceActivated.class))
                .containsExactly(priceActivated(OFFER_ID, pln(30989), JANUARY_1, POLL_INSTANT));
    }

    @Test
    void writesTheMarkerWithTheFactSoARestartedPollEmitsNothing() {
        transactional(() -> repository.save(priceDueLongAgo()));

        transactional(activation::activateDueEntries);
        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.PriceActivated.class)).hasSize(1);
        PricingModel readModel = transactional(() -> repository.get(OFFER_ID)).orElseThrow()
                .toPricingModel(POLL_INSTANT);
        assertThat(readModel.current()).extracting(PriceEntry::activated).containsExactly(true);
    }

    @Test
    void findsAnOfferThroughItsDiscountAndActivatesIt() {
        transactional(() -> repository.save(discountDueLongAgo()));

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.DiscountActivated.class))
                .containsExactly(discountActivated(OFFER_ID, "D-1", JANUARY_1, POLL_INSTANT));
        assertThat(recorded.of(PricingEvent.PriceActivated.class)).isEmpty();
    }

    @Test
    void selectsAnOfferOnceWhenBothItsPriceAndItsDiscountAreDue() {
        transactional(() -> repository.save(priceAndDiscountDueLongAgo()));

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.class)).containsExactly(
                priceActivated(OFFER_ID, pln(30989), JANUARY_1, POLL_INSTANT),
                discountActivated(OFFER_ID, "D-1", JANUARY_1, POLL_INSTANT));
    }

    @Test
    void activatesEveryOfferOfATick() {
        transactional(() -> {
            repository.save(priceDueLongAgo());
            repository.save(priceDueLongAgo(OTHER_OFFER_ID));
        });

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.PriceActivated.class)).extracting(PricingEvent.PriceActivated::offerId)
                .containsExactlyInAnyOrder(OFFER_ID, OTHER_OFFER_ID);
    }

    @Test
    void activatesAnEntryThatStartedAndEndedBeforeTheFirstPoll() {
        transactional(() -> repository.save(historicPriceDueLongAgo()));

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.PriceActivated.class))
                .containsExactly(priceActivated(OFFER_ID, pln(30989), JANUARY_1, POLL_INSTANT));
    }

    @Test
    void aPriceThatIsNotDueYetStaysSilent() {
        transactional(() -> repository.save(priceDueInTheFuture()));

        transactional(activation::activateDueEntries);

        assertThat(recorded.of(PricingEvent.PriceActivated.class)).isEmpty();
    }

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock pricingClock() {
            return Clock.fixed(POLL_INSTANT, ZoneOffset.UTC);
        }
    }
}
