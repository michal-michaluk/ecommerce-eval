package shop.shopping.basket;

import java.time.Instant;
import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * Element 08's read record: one offer as the catalog answered about it, in the provider's own terms.
 *
 * <p>It is a separate type from {@link OfferObservation}, which is the decision's input, and the split is deliberate (D53).
 * The decision may read identity, {@code offerState}, {@code stock} and {@code readAt} and nothing else — a price is nothing
 * availability may be decided from — while a row renders a title, one picture and the price it is actually sold at. Widening
 * {@link OfferObservation} would hand the price to the decision and leave {@link OfferAvailabilityDecision} nothing to prove
 * about it; keeping the two apart makes the mapping explicit instead: this record is the only type carrying the list
 * {@code price} beside {@code currentPrice}, {@link OfferObservationCommand} is the only thing a row is refreshed from, and it
 * carries no list price at all (D53, element 08 §Fields consumed).
 *
 * <p>{@code description} is absent when nobody answered about the offer's content: a failed read ({@code notRead}) carries
 * the identity, the instant and nothing else, so a row keeps the snapshot it already has and only its availability moves (A4,
 * element 02 RULE-8). A {@code removed} offer may come with or without it — the provider either withdrew an offer it can still
 * describe, or answered a {@code 200} without that id — and the basket treats both the same.
 *
 * <p>{@code stock} is the warehouse's answer and this record never repairs it (D56): a count below zero stays a count and the
 * decision reads it as none left, because replacing it with {@code notRead} would turn a malformed answer into "available,
 * stale" — fail-open, where A4 is fail-closed.
 *
 * @param offerId     the offer this record answers for, the identity the application path matches a row on
 * @param description what the provider said about the offer itself, absent when it said nothing about the content
 * @param offerState  whether the catalog offers it, withdrew it, or could not be read at all
 * @param stock       what the warehouse answered, or that it did not answer
 * @param readAt      when the answer was obtained; two observations of one offer are ordered by it (EVT-3)
 */
record OfferRecord(
        String offerId,
        OfferDescription description,
        OfferObservation.OfferState offerState,
        OfferObservation.Stock stock,
        Instant readAt) {

    /** The label element 08 states for a row whose provider named no seller — never merged with another (02 RULE-7). */
    static final String UNKNOWN_SELLER = "unknown seller";

    /**
     * What the provider said about the offer's own content, field by field and never as a whole: element 08's table answers
     * every gap with the row's last-known snapshot, so a {@code 200} without a title keeps the title the row already renders
     * instead of blanking it (element 02 RULE-8), and a field nobody consumes is free to be absent.
     *
     * <p>Every component is therefore optional, and absent means exactly "the provider did not say" — never a value to
     * fabricate. {@code seller} is the one the basket then replaces on the row as the shipment it is grouped into, through
     * {@link #sellerOrPlaceholder()}; {@code price} is the catalog's list price, which no row, line or handover may hold
     * (D53), so its absence is no loss and its presence no permission.
     *
     * @param seller       the seller the row is grouped into as a shipment, absent when the provider named none
     * @param title        the product title, one line in the row
     * @param pictureUrl   the one picture the row shows
     * @param price        the catalog's list price, element 03 — never a row's price (D53)
     * @param currentPrice the price the offer is sold at, flattened by the provider (D22, D40)
     * @param discount     the discount amount the provider flattened, never a rule (D22, Q18/Q19)
     */
    record OfferDescription(String seller, String title, String pictureUrl, Money price, Money currentPrice,
            Money discount) {

        OfferDescription {
            ensureOneCurrency(price, currentPrice, discount);
        }

        /**
         * The currency guard of an answer the adapter built.
         *
         * <p>A record with two currencies in it is a programming error, so it is refused with a JDK type exactly as an absent
         * component is, not with {@link shop.shopping.kernel.ShoppingException} — a client never builds this record (the
         * {@link Money} split, element 03). {@link BasketItem.LastKnown} keeps its own guard for the document it persists;
         * the two are not redundant, the first protects the boundary and the second protects what is stored.
         */
        private static void ensureOneCurrency(Money price, Money currentPrice, Money discount) {
            if (!sameCurrency(price, currentPrice) || !sameCurrency(currentPrice, discount)
                    || !sameCurrency(price, discount)) {
                throw new IllegalArgumentException("one offer is priced in one currency, got " + price + ", "
                        + currentPrice + " and " + discount);
            }
        }

        private static boolean sameCurrency(Money left, Money right) {
            return left == null || right == null || left.currency().equals(right.currency());
        }
    }

    OfferRecord {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(offerState, "offerState");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(readAt, "readAt");
        ensureDescriptionMatchesTheAnswer(offerState, description);
    }

    /**
     * The slice of this record the decision consumes — the observation, and no price on it (D53, element 06 §Interface).
     *
     * @return the decision's input
     */
    OfferObservation observation() {
        return new OfferObservation(offerId, offerState, stock, readAt);
    }

    /**
     * The seller the row is grouped into: the provider's own, or a placeholder unique to this offer so two rows whose
     * sellers are unknown are never charged one shipment rate (element 08 §Fields consumed, 02 RULE-7).
     *
     * @return the seller, never absent
     */
    String sellerOrPlaceholder() {
        String seller = description == null ? null : description.seller();
        return seller == null ? UNKNOWN_SELLER + " " + offerId : seller;
    }

    /**
     * The record's one invariant: a read that failed describes nothing. Everything else the provider is free to answer — an
     * offer that is gone may still be described or may be an id a {@code 200} left out, and a named field may be missing —
     * and a record without a description only means the row keeps the snapshot it already has (element 02 RULE-8).
     */
    private static void ensureDescriptionMatchesTheAnswer(OfferObservation.OfferState offerState,
            OfferDescription description) {
        if (offerState == OfferObservation.OfferState.notRead && description != null) {
            throw new IllegalArgumentException("a read that failed describes nothing, got " + description);
        }
    }
}
