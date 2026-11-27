package shop.shopping.kernel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The handover payload of element 09 as its two sides build it: one row of the capture, one handover per revision, the four
 * totals of element 04 and the range element 05 answers.
 *
 * <p>Every value is built here rather than inline, because a payload is far wider than two parameters
 * ({@code test.new-needs-factory}) and because a test of either side should read in the vocabulary of the element it proves. The
 * digest is the real one, computed by the payload itself: {@link Handover} refuses a digest that is not the digest of the
 * content it carries, so a fixture cannot fake it.
 */
public class HandoverFixture {

    public static final String BASKET_ID = "B-7f3a";
    public static final String CUSTOMER_ID = "C-42";
    public static final String OFFER_ID = "OF-1001";
    public static final String SELLER = "UGREEN_POLSKA";

    public static final Instant CREATED_AT = Instant.parse("2026-11-26T10:00:00Z");

    public static final LocalDate FROM = LocalDate.of(2026, 11, 28);
    public static final LocalDate TO = LocalDate.of(2026, 11, 30);

    public static final long PRODUCT_VALUE = 99890;
    public static final long DELIVERY = 9990;

    private HandoverFixture() {
    }

    public static Handover aHandover(long revision) {
        return aHandoverOf(BASKET_ID, revision);
    }

    public static Handover aHandoverOf(String basketId, long revision) {
        return aHandoverWith(basketId, revision, theDigestOf(basketId, List.of(aLine())));
    }

    public static Handover aHandoverWith(String basketId, long revision, String contentDigest) {
        return aHandover(basketId, revision, contentDigest, List.of(aLine()), anEstimatedRange());
    }

    /** The same basket and revision carrying another row — what a basket sends when the payload it stored is behind. */
    public static Handover aHandoverOfOtherContent(long revision) {
        return aHandover(BASKET_ID, revision, theDigestOf(BASKET_ID, List.of(aSecondLine())),
                List.of(aSecondLine()), anEstimatedRange());
    }

    /** The same content with another delivery range — the one difference that is not part of the identity (D64). */
    public static Handover aHandoverAnsweredWith(Estimate estimate, long revision) {
        return aHandover(BASKET_ID, revision, theDigestOf(BASKET_ID, List.of(aLine())), List.of(aLine()), estimate);
    }

    public static Handover.Line aLine() {
        return aLineOf(OFFER_ID, 1);
    }

    public static Handover.Line aSecondLine() {
        return aLineOf("OF-1002", 1);
    }

    public static Handover.Line aLineOf(String offerId, int quantity) {
        return new Handover.Line(offerId, SELLER, quantity, Availability.available(), MoneyFixture.aCurrentPrice(),
                Money.zero(MoneyFixture.PLN, Money.Kind.discount), MoneyFixture.aCurrentPrice().times(quantity));
    }

    /** The capture's offer carrying a catalog discount, which only the discount amount states. */
    public static Handover.Line aDiscountedLine() {
        return new Handover.Line(OFFER_ID, SELLER, 1, Availability.available(), MoneyFixture.aCurrentPrice(),
                MoneyFixture.aDiscount(), MoneyFixture.aCurrentPrice());
    }

    public static Handover.Totals theTotalsOfOneItem() {
        return totalsOf(PRODUCT_VALUE, 0, DELIVERY);
    }

    public static Handover.Totals totalsOf(long productValue, long discountValue, long delivery) {
        return totalsIn(MoneyFixture.PLN, productValue, discountValue, delivery);
    }

    public static Handover.Totals totalsIn(String currency, long productValue, long discountValue, long delivery) {
        return new Handover.Totals(new Money(productValue, currency),
                new Money(discountValue, currency, Money.Kind.discount),
                new Money(delivery, currency), new Money(productValue + delivery, currency));
    }

    public static HandoverRefusal aRefusal(String offerId, Availability.Status status, boolean stale) {
        return new HandoverRefusal(offerId, status, stale);
    }

    public static CheckoutHandover aCheckoutHandover(String handoverId, long revision, String checkoutId) {
        return new CheckoutHandover(handoverId, revision, checkoutId);
    }

    public static Estimate anEstimatedRange() {
        return Estimate.estimatedBetween(FROM, TO, Estimate.Source.configured);
    }

    public static Estimate anUnknownEstimate() {
        return Estimate.unknown(Estimate.Source.configured);
    }

    public static String theDigestOf(String basketId, List<Handover.Line> selection) {
        return Handover.contentDigestOf(basketId, CUSTOMER_ID, selection, theTotalsOfOneItem());
    }

    private static Handover aHandover(String basketId, long revision, String contentDigest, List<Handover.Line> selection,
            Estimate estimate) {
        return new Handover(basketId, basketId, CUSTOMER_ID, MoneyFixture.PLN, revision, CREATED_AT, contentDigest,
                selection, theTotalsOfOneItem(), estimate);
    }
}
