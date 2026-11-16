package shop.catalog.pricing;

import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescription;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** Instance A of 03 plus the pieces the 10 scenarios need; every instant is explicit, no test reads a wall clock. */
public class PricingFixture {

    public static final String OFFER_ID = "OFF-75423";
    public static final String PLN = "PLN";
    public static final String EUR = "EUR";
    public static final String JPY = "JPY";

    public static final Instant APRIL_20 = Instant.parse("2026-04-20T00:00:00Z");
    public static final Instant JANUARY_1 = Instant.parse("2026-01-01T00:00:00Z");
    public static final Instant MAY_1 = Instant.parse("2026-05-01T00:00:00Z");
    public static final Instant JUNE_1 = Instant.parse("2026-06-01T00:00:00Z");
    public static final Instant JUNE_15 = Instant.parse("2026-06-15T00:00:00Z");
    public static final Instant JUNE_20 = Instant.parse("2026-06-20T00:00:00Z");
    public static final Instant JULY_1 = Instant.parse("2026-07-01T00:00:00Z");
    public static final Instant JULY_8 = Instant.parse("2026-07-08T00:00:00Z");
    public static final Instant JULY_15 = Instant.parse("2026-07-15T00:00:00Z");

    public static final PriceEntry PRICE_30989 = priceEntry("P-1", pln(30989), MAY_1, null);

    public static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    public static Money pln(int amountMinor) {
        return new Money(amountMinor, PLN);
    }

    public static Money eur(int amountMinor) {
        return new Money(amountMinor, EUR);
    }

    public static Money jpy(int amountMinor) {
        return new Money(amountMinor, JPY);
    }

    public static PriceId priceId(String value) {
        return new PriceId(value);
    }

    public static DiscountId discountId(String value) {
        return new DiscountId(value);
    }

    public static Validity openFrom(Instant validFrom) {
        return Validity.startingAt(validFrom);
    }

    public static Validity until(Instant validFrom, Instant validUntil) {
        return Validity.between(validFrom, validUntil);
    }

    public static PriceEntry priceEntry(String priceId, Money price, Instant validFrom, Instant validUntil) {
        return PriceEntry.scheduled(priceId(priceId), price, until(validFrom, validUntil));
    }

    public static PriceChange priceChange(String priceId, Money price, Instant validFrom, Instant validUntil) {
        return new PriceChange(priceId(priceId), price, until(validFrom, validUntil));
    }

    public static PriceChange openPriceChange(String priceId, Money price, Instant validFrom) {
        return new PriceChange(priceId(priceId), price, openFrom(validFrom));
    }

    public static Discount percentageDiscount(
            String discountId, int basisPoints, Instant validFrom, Instant validUntil) {
        return Discount.scheduled(discountId(discountId), new Discount.Kind.Percentage(basisPoints),
                until(validFrom, validUntil));
    }

    public static Discount openPercentageDiscount(String discountId, int basisPoints, Instant validFrom) {
        return Discount.scheduled(discountId(discountId), new Discount.Kind.Percentage(basisPoints),
                openFrom(validFrom));
    }

    public static Discount fixedDiscount(String discountId, Money amount, Instant validFrom, Instant validUntil) {
        return Discount.scheduled(
                discountId(discountId), new Discount.Kind.Fixed(amount), until(validFrom, validUntil));
    }

    public static DiscountChange percentageChange(
            String discountId, int basisPoints, Instant validFrom, Instant validUntil) {
        return new DiscountChange(discountId(discountId), new Discount.Kind.Percentage(basisPoints),
                until(validFrom, validUntil));
    }

    public static DiscountChange fixedChange(String discountId, Money amount, Instant validFrom, Instant validUntil) {
        return new DiscountChange(
                discountId(discountId), new Discount.Kind.Fixed(amount), until(validFrom, validUntil));
    }

    public static PricingService serviceAt(PricingRepository models, Clock clock) {
        return serviceAt(models, clock, knowingEveryOffer());
    }

    public static PricingService serviceAt(PricingRepository models, Clock clock, KnownOffer knownOffer) {
        return new PricingService(models, ActivePriceCalculation.defaultCalculation(), knownOffer, clock);
    }

    /** The offers this test believes exist: the pricing context only ever asks whether the identity is known (17 RULE-4). */
    public static KnownOffer knowingEveryOffer() {
        return offerId -> true;
    }

    public static KnownOffer knowing(String... offerIds) {
        return java.util.Set.of(offerIds)::contains;
    }

    public static PricingModelEditor emptyPricingModel() {
        return PricingModelEditor.newPricingModelFor(OFFER_ID);
    }

    public static PricingModelEditor pricingModelWithOneOpenPrice() {
        PricingModelEditor editor = emptyPricingModel();
        editor.schedulePrice(openPriceChange("P-1", pln(30989), MAY_1), APRIL_20);
        return editor;
    }

    /** Instance A of 03: one current price, one scheduled change and two discounts, all decided on 20 April. */
    public static PricingModelEditor pricingModelOfInstanceA() {
        PricingModelEditor editor = emptyPricingModel();
        editor.schedulePrice(priceChange("P-1", pln(30989), MAY_1, JULY_1), APRIL_20);
        editor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), APRIL_20);
        editor.scheduleDiscount(percentageChange("D-1", 1000, JUNE_20, JULY_1), APRIL_20);
        editor.scheduleDiscount(fixedChange("D-2", pln(2000), JULY_1, JULY_8), APRIL_20);
        return editor;
    }

    /** The offer snapshot the pricing context listens to: the fact it keeps is only that this offer exists. */
    public static Offer publishedOffer(String offerId) {
        return new Offer(offerId, Offer.State.PUBLISHED, 1, LocalDate.of(2026, 7, 1),
                ProductDescription.Draft.builder().build(), List.of());
    }
}
