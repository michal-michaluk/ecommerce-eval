package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.shopping.IntegrationTest;
import shop.shopping.kernel.Estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.THE_CLOCK;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aRequestOf;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimatedRange;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithDelivery;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithoutDelivery;
import static shop.shopping.basket.BasketFixture.aFirstItem;
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
}
