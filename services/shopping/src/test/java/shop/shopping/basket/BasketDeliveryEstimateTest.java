package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import shop.shopping.basket.BasketConfiguration.Settings;
import shop.shopping.basket.BasketConfiguration.Settings.EstimateSettings.DefaultDays;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.AFTER_MIDNIGHT_IN_WARSAW;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM_DAYS;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.LAST_DAY_OF_NOVEMBER;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.LATE_EVENING;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.THE_CLOCK;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.THE_DAY_IN_KIRITIMATI;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.THE_WARSAW_DAY_AFTER;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO_DAYS;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TODAY;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.UNKNOWN;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.ZONE;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aDeselected;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aFixedClockAt;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aRequestOf;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.aSmartRequestOf;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimate;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimateConfiguredWith;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimateWithoutConfiguration;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimatedRange;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anUnknownEstimate;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.noEstimateSettings;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsInAnotherZone;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithAnUnknownZone;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithAnotherRange;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithoutDefaultDays;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithoutFallback;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.settingsWithoutZone;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.theConfiguredSettings;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.theDeployedProperties;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.theDeployedPropertiesWithoutTheBlock;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.aThirdItemOfAnotherSeller;
import static shop.shopping.basket.BasketFixture.anItemReadAsStale;
import static shop.shopping.basket.BasketFixture.anOutOfStockItem;
import static shop.shopping.basket.BasketFixture.anUnavailableItem;

/**
 * Element 05's interface scenarios I1–I7, one test per row, plus what the rows cannot isolate: the two calendar edges a range
 * counted in {@code Europe/Warsaw} makes possible to get wrong (a late evening on the last day of a month, and a UTC instant
 * the customer's calendar has already left behind), each of the three configuration keys being genuinely read rather than
 * silently defaulted, a {@code stale} row that blocks nothing, a basket with no row at all, and the per-shipment granularity
 * the caller owns.
 *
 * <p>Every test counts from a fixed {@link java.time.Clock} — the policy reads no clock of its own ({@code clock.injected}), so
 * the instant the range starts from is stated by the test and never by the machine that runs it. I1 asserts {@code kind},
 * {@code from}, {@code to} and {@code source} one by one, because it is the row that fixes the shape; the remaining rows
 * compare whole values, which pins the same four components and nothing else.
 *
 * <p>Two tests bind {@link Settings} from the deployment's own property names without a Spring context, because element 05
 * §Configuration fixes the keys and not just the record: a renamed component or a key the binder cannot reach would answer
 * {@code unknown} everywhere while every scenario row stayed green.
 */
class BasketDeliveryEstimateTest {

    @Test
    void i1EstimatesTheConfiguredRangeOverTheSelectedAvailableRows() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, aFirstItem(), aSecondItem()));

        assertThat(estimate.kind()).isEqualTo(Estimate.Kind.estimate);
        assertThat(estimate.from()).isEqualTo(FROM);
        assertThat(estimate.to()).isEqualTo(TO);
        assertThat(estimate.source()).isEqualTo(Estimate.Source.configured);
    }

    @Test
    void i2AnOutOfStockRowContributesNothing() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, aFirstItem(), anOutOfStockItem()));

        assertThat(estimate).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void i3AnUnavailableRowContributesNothing() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, aFirstItem(), anUnavailableItem()));

        assertThat(estimate).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void i4NothingSelectedIsUnknown() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, aDeselected(aFirstItem())));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void i5AnAbsentConfigurationKeyAnswersTheFallback() {
        Estimate estimate = anEstimateConfiguredWith(settingsWithoutDefaultDays())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void i6SmartDoesNotChangeTheRange() {
        Estimate estimate = anEstimate().estimate(aSmartRequestOf(THE_CLOCK, aFirstItem(), aSecondItem()));

        assertThat(estimate).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void i7EveryRowBlockedIsUnknown() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, anOutOfStockItem(), anUnavailableItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void aLateEveningOnTheLastDayOfTheMonthStillCountsTheWarsawDay() {
        Estimate estimate = anEstimate().estimate(aRequestOf(aFixedClockAt(LATE_EVENING), aFirstItem()));

        assertThat(estimate).isEqualTo(
                anEstimatedRange(LAST_DAY_OF_NOVEMBER.plusDays(2), LAST_DAY_OF_NOVEMBER.plusDays(4)));
    }

    @Test
    void aUtcInstantOnAnotherDayCountsTheWarsawDay() {
        Estimate estimate = anEstimate().estimate(aRequestOf(aFixedClockAt(AFTER_MIDNIGHT_IN_WARSAW), aFirstItem()));

        assertThat(estimate).isEqualTo(
                anEstimatedRange(THE_WARSAW_DAY_AFTER.plusDays(2), THE_WARSAW_DAY_AFTER.plusDays(4)));
    }

    @Test
    void aStaleRowAloneNeverBlocksTheEstimate() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK, anItemReadAsStale()));

        assertThat(estimate).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void aBasketWithNoRowHasNothingToEstimate() {
        Estimate estimate = anEstimate().estimate(aRequestOf(THE_CLOCK));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void anAbsentBlockAnswersTheFallback() {
        Estimate estimate = anEstimateWithoutConfiguration().estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void aPresentBlockWithoutKeysAnswersTheFallback() {
        Estimate estimate = anEstimateConfiguredWith(noEstimateSettings()).estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void anAbsentZoneAnswersTheFallback() {
        Estimate estimate = anEstimateConfiguredWith(settingsWithoutZone()).estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void aConfigurationWithoutFallbackIsUnusable() {
        Estimate estimate = anEstimateConfiguredWith(settingsWithoutFallback())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void anUnknownZoneAnswersTheFallbackInsteadOfThrowing() {
        Estimate estimate = anEstimateConfiguredWith(settingsWithAnUnknownZone())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anUnknownEstimate());
    }

    @Test
    void theConfiguredRangeIsTheOneCountedNotTheHardcodedOne() {
        Estimate estimate = anEstimateConfiguredWith(settingsWithAnotherRange())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anEstimatedRange(TODAY.plusDays(1), TODAY.plusDays(3)));
    }

    @Test
    void theConfiguredZoneDecidesTheCalendarNotWarsaw() {
        Estimate estimate = anEstimateConfiguredWith(settingsInAnotherZone())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(
                anEstimatedRange(THE_DAY_IN_KIRITIMATI.plusDays(2), THE_DAY_IN_KIRITIMATI.plusDays(4)));
    }

    @Test
    void aFullyConfiguredBlockAnswersTheSameRangeAsTheHardcodedDefault() {
        Estimate estimate = anEstimateConfiguredWith(theConfiguredSettings())
                .estimate(aRequestOf(THE_CLOCK, aFirstItem()));

        assertThat(estimate).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void mixedSellersInOneRequestAnswerTheSameDaysAsOneShipment() {
        Estimate oneShipment = anEstimate().estimate(aRequestOf(THE_CLOCK, aFirstItem(), aSecondItem()));
        Estimate twoShipments = anEstimate().estimate(aRequestOf(THE_CLOCK, aFirstItem(), aThirdItemOfAnotherSeller()));

        assertThat(twoShipments).isEqualTo(oneShipment);
    }

    @Test
    void theThreeConfiguredKeysBindToTheSettingsRecord() {
        Settings bound = new Binder(new MapConfigurationPropertySource(theDeployedProperties()))
                .bind("basket", Bindable.of(Settings.class))
                .get();

        assertThat(bound.estimate().defaultDays()).isEqualTo(new DefaultDays(FROM_DAYS, TO_DAYS));
        assertThat(bound.estimate().zone()).isEqualTo(ZONE);
        assertThat(bound.estimate().fallback()).isEqualTo(UNKNOWN);
    }

    @Test
    void anAbsentBlockBindsNothing() {
        Settings bound = new Binder(new MapConfigurationPropertySource(theDeployedPropertiesWithoutTheBlock()))
                .bind("basket", Bindable.of(Settings.class))
                .get();

        assertThat(bound.estimate()).isNull();
    }
}
