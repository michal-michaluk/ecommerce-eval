package shop.catalog.reads;

import shop.catalog.offer.Media;
import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescription;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.pricing.PricingChanged;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The facts the three read models are built from: the moments of 11's scenarios, offers in every lifecycle state, and a price series
 * whose entries sit either side of the instant a test asks about.
 *
 * <p>The pricing instants are relative to {@code now} on purpose: the pricing view partitions its rows by the clock it is served
 * with, so an absolute date would make the test's expectation depend on the day it runs.
 */
public class ReadsFixture {

    public static final String OFFER_ID = "PRD-75423";
    public static final String REVIEWER = "reviewer.kowalska";
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Warsaw");
    public static final String PLN = "PLN";
    public static final String EUR = "EUR";

    /** The business date 11's scenarios are stated on. */
    public static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private ReadsFixture() {
    }

    public static Clock clockOn(LocalDate date) {
        return clockOn(date, LocalTime.NOON);
    }

    /** A clock whose instant is {@code time} in the business timezone, so {@code today} is the date the scenario names. */
    public static Clock clockOn(LocalDate date, LocalTime time) {
        return Clock.fixed(date.atTime(time).atZone(BUSINESS_ZONE).toInstant(), ZoneOffset.UTC);
    }

    public static OfferVisibility visibilityOn(LocalDate today) {
        return OfferVisibility.of(clockOn(today), BUSINESS_ZONE.getId(), OfferVisibility.Future.HIDDEN);
    }

    public static OfferVisibility announceOn(LocalDate today) {
        return OfferVisibility.of(clockOn(today), BUSINESS_ZONE.getId(), OfferVisibility.Future.ANNOUNCE);
    }

    public static Offer draftOffer(String offerId) {
        return offer(offerId, Offer.State.DRAFT, null);
    }

    public static Offer publishedOffer(String offerId, LocalDate availabilityStart) {
        return offer(offerId, Offer.State.PUBLISHED, availabilityStart);
    }

    public static Offer removedOffer(String offerId) {
        return offer(offerId, Offer.State.REMOVED, LocalDate.of(2026, 5, 10));
    }

    public static Offer offer(Offer.State state, LocalDate availabilityStart) {
        return offer(OFFER_ID, state, availabilityStart);
    }

    public static Offer offer(String offerId, Offer.State state, LocalDate availabilityStart) {
        return offer(offerId, state, availabilityStart, ProductDescriptionDraftFixture.draftOfInstanceA());
    }

    public static Offer offer(String offerId, Offer.State state, LocalDate availabilityStart, ProductDescription.Draft draft) {
        return offer(offerId, state, availabilityStart, draft, media());
    }

    public static Offer offer(String offerId, Offer.State state, LocalDate availabilityStart,
                              ProductDescription.Draft draft, List<Media> media) {
        return new Offer(offerId, state, 1, availabilityStart, draft, media);
    }

    public static List<Media> media() {
        return List.of(
                new Media("M-01", Media.Kind.IMAGE, 1, true, "jpg", "https://cdn.example/M-01.jpg"),
                new Media("M-02", Media.Kind.IMAGE, 2, false, "jpg", "https://cdn.example/M-02.jpg"));
    }

    /**
     * One offer's whole price series around {@code now}: one closed entry, one open entry and one that starts later, plus a discount
     * of each state. The partitions of 16 are what a render derives from exactly these four entries.
     */
    public static PricingChanged priceSeries(String offerId, Instant now) {
        return new PricingChanged(offerId,
                List.of(
                        new PricingChanged.PriceRow("P-0", 31900, PLN,
                                now.minusSeconds(120 * 86_400L), now.minusSeconds(60 * 86_400L)),
                        new PricingChanged.PriceRow("P-1", 30989, PLN,
                                now.minusSeconds(60 * 86_400L), null),
                        new PricingChanged.PriceRow("P-2", 29900, PLN,
                                now.plusSeconds(30 * 86_400L), null),
                        new PricingChanged.PriceRow("P-9", 7000, EUR,
                                now.minusSeconds(60 * 86_400L), null)),
                List.of(
                        new PricingChanged.DiscountRow("D-0", PricingChanged.DiscountRow.Kind.PERCENTAGE, 500, 0, null,
                                now.minusSeconds(120 * 86_400L), now.minusSeconds(60 * 86_400L)),
                        new PricingChanged.DiscountRow("D-1", PricingChanged.DiscountRow.Kind.PERCENTAGE, 1000, 0, null,
                                now.minusSeconds(10 * 86_400L), null),
                        new PricingChanged.DiscountRow("D-2", PricingChanged.DiscountRow.Kind.FIXED, 0, 2000, PLN,
                                now.plusSeconds(30 * 86_400L), null)));
    }

    /** The same series without the foreign-currency entry, for a test that cares only about the PLN screen. */
    public static PricingChanged plnPriceSeries(String offerId, Instant now) {
        PricingChanged series = priceSeries(offerId, now);
        return new PricingChanged(offerId,
                series.prices().stream().filter(price -> PLN.equals(price.currency())).toList(),
                series.discounts());
    }

    /** A series a test states entry by entry, so its expectation reads as the price table it is about. */
    public static PricingChanged series(
            String offerId, List<PricingChanged.PriceRow> prices, List<PricingChanged.DiscountRow> discounts) {
        return new PricingChanged(offerId, prices, discounts);
    }

    public static PricingChanged.PriceRow priceRow(
            String priceId, int amountMinor, String currency, Instant validFrom, Instant validUntil) {
        return new PricingChanged.PriceRow(priceId, amountMinor, currency, validFrom, validUntil);
    }
}
