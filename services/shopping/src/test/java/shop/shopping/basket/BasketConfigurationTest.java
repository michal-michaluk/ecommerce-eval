package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.shopping.IntegrationTest;
import shop.shopping.kernel.Estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.THE_CLOCK;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aRequestOf;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimatedRange;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithDelivery;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithoutDelivery;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.OfferSeedFixture.A_CURRENT_PRICE_IN_PLN;
import static shop.shopping.basket.OfferSeedFixture.NO_DISCOUNT_IN_PLN;
import static shop.shopping.basket.OfferSeedFixture.aFixtureOf;
import static shop.shopping.basket.OfferSeedFixture.aSeededOffer;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferReadZeroTimes;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWhoseStockWasNeverRead;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithABlankTitle;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutAnswers;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutCurrency;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutId;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithAnEmptyAnswerQueue;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutListPrice;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutPicture;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutSeller;
import static shop.shopping.basket.OfferSeedFixture.aSeededOfferWithoutTitle;
import static shop.shopping.basket.BasketPriceFixture.aBasketOf;
import static shop.shopping.basket.BasketPriceFixture.aPrice;
import static shop.shopping.basket.BasketPriceFixture.theFirstItem;

/**
 * The two policies of elements 04 and 05 as the deployment wires them: the {@code basket.delivery.*} and
 * {@code basket.estimate.*} blocks reach the beans. That is the difference between a basket whose delivery line shows a range
 * and one whose line is hidden — DoV5 reads 100 % only while these keys arrive (D58).
 *
 * <p>The delivery half runs against the integration profile's own rate, deliberately unlike the stated default: with the
 * deployed {@code 9 990} a bean that ignored the block and fell back to {@link DeliveryRates#defaultRates()} would answer the
 * same price and prove nothing. The estimate half runs against the shipped {@code application.yml} block, which the profile
 * does not override.
 */
@IntegrationTest
class BasketConfigurationTest {

    private static final long THE_PROFILE_RATE = 12340;

    @Autowired
    BasketPriceCalculation calculation;

    @Autowired
    BasketDeliveryEstimate estimate;

    @Test
    void chargesTheDeliveryTheProfileStates() {
        assertThat(calculation.calculate(aBasketOf(theFirstItem()), Channel.standard))
                .isEqualTo(aPrice(99890, 0, THE_PROFILE_RATE, 99890 + THE_PROFILE_RATE));
    }

    @Test
    void keepsChargingSmartWhenTheProfileDoesNotFreeIt() {
        assertThat(calculation.calculate(aBasketOf(theFirstItem()), Channel.smart))
                .isEqualTo(aPrice(99890, 0, THE_PROFILE_RATE, 99890 + THE_PROFILE_RATE));
    }

    @Test
    void answersTheRangeTheDeploymentStates() {
        Estimate range = estimate.estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(range).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void answersTheStatedRateForABlockThatOmitsTheRate() {
        assertThat(BasketConfiguration.deliveryRatesOf(settingsWithDelivery(null, false)))
                .isEqualTo(new DeliveryRates(DeliveryRates.defaultRates().ratePerShipment(), false));
    }

    @Test
    void keepsTheSmartRuleForABlockThatOmitsIt() {
        assertThat(BasketConfiguration.deliveryRatesOf(settingsWithDelivery(12_340L, null)))
                .isEqualTo(new DeliveryRates(12_340, DeliveryRates.defaultRates().freeWhenSmart()));
    }

    @Test
    void answersTheWholeDefaultForAnAbsentBlock() {
        assertThat(BasketConfiguration.deliveryRatesOf(settingsWithoutDelivery()))
                .isEqualTo(DeliveryRates.defaultRates());
    }

    @Test
    void convertsTheOffersTheDeploymentSeeds() {
        FixtureOfferCatalog.Offering seeded =
                BasketConfiguration.offeringsOf(aFixtureOf(aSeededOffer("OF-1002", "9", 2))).get(0);

        assertThat(seeded.offerId()).isEqualTo("OF-1002");
        assertThat(seeded.offerState()).isEqualTo(OfferObservation.OfferState.onSale);
        assertThat(seeded.stockAnswers()).containsExactly(new OfferObservation.Stock.Quantity(9),
                new OfferObservation.Stock.Quantity(9), new OfferObservation.Stock.Quantity(0));
        assertThat(seeded.description()).satisfies(described -> {
            assertThat(described.seller()).isEqualTo("UGREEN_POLSKA");
            assertThat(described.currentPrice()).isEqualTo(A_CURRENT_PRICE_IN_PLN);
            assertThat(described.discount()).isEqualTo(NO_DISCOUNT_IN_PLN);
        });
    }

    @Test
    void seedsNothingWhenTheDeploymentStatesNoOffer() {
        assertThat(BasketConfiguration.offeringsOf(null)).isEmpty();
        assertThat(BasketConfiguration.offeringsOf(aFixtureOf())).isEmpty();
    }

    @Test
    void refusesASeedThatCannotDescribeARow() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutTitle("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no title");
    }

    @Test
    void refusesAStockThatIsNeitherACountNorSilence() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOffer("OF-1002", "many", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("neither notRead nor a count");
    }

    @Test
    void refusesASeedThatStatesNoSeller() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutSeller("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no seller");
    }

    @Test
    void refusesASeedThatStatesNoPicture() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutPicture("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no picture");
    }

    @Test
    void refusesASeedThatStatesNoListPrice() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutListPrice("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no list-price");
    }

    @Test
    void refusesASeedThatStatesNoCurrency() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutCurrency("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no currency");
    }

    @Test
    void refusesASeedWhoseTitleIsStatedButBlank() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithABlankTitle("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no title");
    }

    @Test
    void refusesASeedThatAnswersAReadCountOfZero() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferReadZeroTimes("OF-1001"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reads, which is no answer");
    }

    @Test
    void seedsAReadThatAnsweredNothingAsTheOneNeverReadAnswer() {
        FixtureOfferCatalog.Offering seeded =
                BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWhoseStockWasNeverRead("OF-1001"))).get(0);

        assertThat(seeded.stockAnswers()).containsExactly(OfferObservation.Stock.NotRead.notRead);
    }

    @Test
    void seedsTheNeverReadAnswerForASeedThatStatesNoAnswers() {
        FixtureOfferCatalog.Offering seeded =
                BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutAnswers("OF-1001"))).get(0);

        assertThat(seeded.stockAnswers()).containsExactly(OfferObservation.Stock.NotRead.notRead);
    }

    @Test
    void seedsTheNeverReadAnswerForASeedWhoseAnswerQueueIsEmpty() {
        FixtureOfferCatalog.Offering seeded =
                BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithAnEmptyAnswerQueue("OF-1001"))).get(0);

        assertThat(seeded.stockAnswers()).containsExactly(OfferObservation.Stock.NotRead.notRead);
    }

    @Test
    void refusesASeedThatStatesNoId() {
        assertThatThrownBy(() -> BasketConfiguration.offeringsOf(aFixtureOf(aSeededOfferWithoutId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("states no id");
    }

}
