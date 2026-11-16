package shop.catalog.pricing;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The states the RULE-9 poll has to deal with: an entry due long before the poll, an offer the poll can only find
 * through its discount, an entry that already started and ended, and one that is not due at all.
 *
 * <p>These models are built through the editor's all-args constructor on purpose: a command refuses a {@code validFrom}
 * in the past and marks what it schedules, so only a rehydrated or hand-built model can express "this became due while
 * the service was down" (13 RULE-8).
 */
public class ActivationFixture {

    public static final String OTHER_OFFER_ID = "OFF-75375";
    public static final Instant POLL_INSTANT = Instant.parse("2026-06-01T00:00:00Z");
    public static final Instant FEBRUARY_1 = Instant.parse("2026-02-01T00:00:00Z");
    public static final Instant A_FUTURE_INSTANT = Instant.parse("2030-01-01T00:00:00Z");

    public static PricingModelEditor priceDueLongAgo() {
        return priceDueLongAgo(PricingFixture.OFFER_ID);
    }

    public static PricingModelEditor priceDueLongAgo(String offerId) {
        return model(offerId, List.of(unmarkedPrice(PricingFixture.JANUARY_1, null)), List.of());
    }

    /** No unmarked price left: the only way to this offer is its discount row. */
    public static PricingModelEditor discountDueLongAgo() {
        return model(PricingFixture.OFFER_ID,
                List.of(unmarkedPrice(PricingFixture.JANUARY_1, null).markedActivated()),
                List.of(percentageDiscount()));
    }

    public static PricingModelEditor priceAndDiscountDueLongAgo() {
        return model(PricingFixture.OFFER_ID,
                List.of(unmarkedPrice(PricingFixture.JANUARY_1, null)),
                List.of(percentageDiscount()));
    }

    /** Started and ended before the first poll — {@code isDueAt} ignores {@code validUntil}, so it still activates. */
    public static PricingModelEditor historicPriceDueLongAgo() {
        return model(PricingFixture.OFFER_ID, List.of(unmarkedPrice(PricingFixture.JANUARY_1, FEBRUARY_1)), List.of());
    }

    public static PricingModelEditor priceDueInTheFuture() {
        return model(PricingFixture.OFFER_ID, List.of(unmarkedPrice(A_FUTURE_INSTANT, null)), List.of());
    }

    public static PricingActivationListener poll(PricingService pricing, NormalizingPricingRepository awaiting, Clock clock) {
        return new PricingActivationListener(pricing, awaiting, clock);
    }

    private static PricingModelEditor model(String offerId, List<PriceEntry> prices, List<Discount> discounts) {
        return new PricingModelEditor(offerId, new ArrayList<>(), prices, discounts);
    }

    private static PriceEntry unmarkedPrice(Instant validFrom, Instant validUntil) {
        return PricingFixture.priceEntry("P-1", PricingFixture.pln(30989), validFrom, validUntil);
    }

    private static Discount percentageDiscount() {
        return PricingFixture.percentageDiscount("D-1", 1000, PricingFixture.JANUARY_1, FEBRUARY_1);
    }
}
