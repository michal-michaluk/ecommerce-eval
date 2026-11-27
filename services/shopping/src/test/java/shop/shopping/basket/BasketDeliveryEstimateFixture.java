package shop.shopping.basket;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import shop.shopping.basket.BasketConfiguration.Settings;
import shop.shopping.basket.BasketConfiguration.Settings.EstimateSettings;
import shop.shopping.basket.BasketConfiguration.Settings.EstimateSettings.DefaultDays;
import shop.shopping.kernel.Estimate;

/**
 * Element 05's interface scenarios I1–I7 and the edges the day range makes possible to get wrong, as test data: the request of
 * one seller shipment, the {@code basket.estimate.*} block with every absence that matters, the deployment properties those
 * keys bind from, and the fixed clocks the customer's day is counted in.
 *
 * <p>Every value is built here rather than inline, because a request and a settings record are wider than two parameters
 * ({@code test.new-needs-factory}) and because a test should read in the vocabulary of its own row —
 * {@code settingsWithoutZone()} says which absence it proves. The clocks are fixed in a named zone (D43), so a test states the
 * instant the calendar is counted from instead of reading one.
 */
public class BasketDeliveryEstimateFixture {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    /** 11:00 in Warsaw — {@link BasketFixture#NOW} is already a Warsaw morning. */
    public static final Instant NOW = BasketFixture.NOW;

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    public static final LocalDate FROM = TODAY.plusDays(2);

    public static final LocalDate TO = TODAY.plusDays(4);

    public static final Clock THE_CLOCK = aFixedClockAt(NOW);

    /** 23:30 in Warsaw on the last day of November — the range leaves the month, the UTC day it leaves from does not. */
    public static final Instant LATE_EVENING = Instant.parse("2026-11-30T22:30:00Z");

    public static final LocalDate LAST_DAY_OF_NOVEMBER = LocalDate.of(2026, 11, 30);

    /** 00:30 in Warsaw — UTC still calls it the 26th while the customer's calendar has already turned to the 27th. */
    public static final Instant AFTER_MIDNIGHT_IN_WARSAW = Instant.parse("2026-11-26T23:30:00Z");

    public static final LocalDate THE_WARSAW_DAY_AFTER = LocalDate.of(2026, 11, 27);

    /** UTC+14 — at {@link #NOW} the customer's calendar is already the 27th, so a zone other than Warsaw moves the range. */
    public static final String KIRITIMATI = "Pacific/Kiritimati";

    public static final LocalDate THE_DAY_IN_KIRITIMATI = LocalDate.of(2026, 11, 27);

    public static final int FROM_DAYS = 2;

    public static final int TO_DAYS = 4;

    public static final String ZONE = "Europe/Warsaw";

    public static final String UNKNOWN = "unknown";

    public static final long DELIVERY_RATE = 9990;

    public static final boolean FREE_WHEN_SMART = true;

    private static final int ANOTHER_FROM_DAYS = 1;

    private static final int ANOTHER_TO_DAYS = 3;

    private BasketDeliveryEstimateFixture() {
    }

    public static Clock aFixedClockAt(Instant now) {
        return Clock.fixed(now, WARSAW);
    }

    public static BasketDeliveryEstimate anEstimate() {
        return BasketDeliveryEstimate.defaultEstimate();
    }

    public static BasketDeliveryEstimate anEstimateConfiguredWith(EstimateSettings settings) {
        return new BasketDeliveryEstimate(settings);
    }

    /** What the wiring produces when the deployment states no {@code basket.estimate} block at all. */
    public static BasketDeliveryEstimate anEstimateWithoutConfiguration() {
        return new BasketDeliveryEstimate(null);
    }

    public static EstimateSettings theConfiguredSettings() {
        return new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), ZONE, UNKNOWN);
    }

    /** The {@code basket.*} block with the delivery half a deployment may state, half-stated included. */
    public static Settings settingsWithDelivery(Long rate, Boolean freeWhenSmart) {
        return new Settings("PLN", null, new Settings.DeliverySettings(rate, freeWhenSmart));
    }

    /** The same block before the deployment states any delivery key. */
    public static Settings settingsWithoutDelivery() {
        return new Settings("PLN", null, null);
    }

    /** A range the hardcoded default does not hold, so a test can tell a configured range from the D36 constant. */
    public static EstimateSettings settingsWithAnotherRange() {
        return new EstimateSettings(new DefaultDays(ANOTHER_FROM_DAYS, ANOTHER_TO_DAYS), ZONE, UNKNOWN);
    }

    /** A zone that is not Warsaw, so a test can tell the configured calendar from the hardcoded one. */
    public static EstimateSettings settingsInAnotherZone() {
        return new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), KIRITIMATI, UNKNOWN);
    }

    public static EstimateSettings settingsWithoutDefaultDays() {
        return new EstimateSettings(null, ZONE, UNKNOWN);
    }

    public static EstimateSettings settingsWithoutZone() {
        return new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), null, UNKNOWN);
    }

    public static EstimateSettings settingsWithoutFallback() {
        return new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), ZONE, null);
    }

    public static EstimateSettings settingsWithAnUnknownZone() {
        return new EstimateSettings(new DefaultDays(FROM_DAYS, TO_DAYS), "Mars/Olympus", UNKNOWN);
    }

    public static EstimateSettings noEstimateSettings() {
        return new EstimateSettings(null, null, null);
    }

    /** The {@code basket.*} properties a deployment states, as the binder reads them. */
    public static Map<String, String> theDeployedProperties() {
        return Map.of(
                "basket.currency", "PLN",
                "basket.delivery.rate", String.valueOf(DELIVERY_RATE),
                "basket.delivery.free-when-smart", String.valueOf(FREE_WHEN_SMART),
                "basket.estimate.default-days.from", String.valueOf(FROM_DAYS),
                "basket.estimate.default-days.to", String.valueOf(TO_DAYS),
                "basket.estimate.zone", ZONE,
                "basket.estimate.fallback", UNKNOWN);
    }

    /** The same deployment before element 05 is configured — the block is not there at all. */
    public static Map<String, String> theDeployedPropertiesWithoutTheBlock() {
        return Map.of("basket.currency", "PLN");
    }

    public static BasketDeliveryEstimate.Request aRequestOf(Clock clock, BasketItem... items) {
        return new BasketDeliveryEstimate.Request(List.of(items), Channel.standard, clock.instant());
    }

    public static BasketDeliveryEstimate.Request aSmartRequestOf(Clock clock, BasketItem... items) {
        return new BasketDeliveryEstimate.Request(List.of(items), Channel.smart, clock.instant());
    }

    public static BasketItem aDeselected(BasketItem item) {
        return item.withSelection(false);
    }

    public static Estimate anEstimatedRange(LocalDate from, LocalDate to) {
        return Estimate.estimatedBetween(from, to, Estimate.Source.configured);
    }

    public static Estimate anUnknownEstimate() {
        return Estimate.unknown(Estimate.Source.configured);
    }
}
