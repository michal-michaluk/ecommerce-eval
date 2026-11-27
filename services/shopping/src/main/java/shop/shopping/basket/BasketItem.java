package shop.shopping.basket;

import java.time.Instant;
import java.util.Objects;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.ShoppingException;

/**
 * One offer in a basket: which offer, whose shipment it belongs to, how many, whether the customer selected it, what
 * element 06 last decided about it, and when that answer was obtained (element 02 RULE-4, RULE-7).
 *
 * <p>The last-known snapshot is kept whatever happens to the offer, so an erased offer still renders as a blocked row
 * instead of disappearing (RULE-5, RULE-8). It is also the price source when no fresh read is available — element 04 sums
 * its {@code currentPrice} and {@code discount} — but it is never re-derived from other fields (D22) and never authoritative:
 * element 09 crosses it as a snapshot and checkout re-prices it (D31).
 *
 * <p>Quantity is at least one — zero is not a quantity and removal is the only exit (RULE-3) — and the item never exists
 * twice for one offer: a repeated add merges the quantity and selects the row (D19).
 *
 * @param offerId      the offer this row stands for
 * @param seller       the seller, which is also the shipment the row is grouped into (RULE-7)
 * @param quantity     how many units, always at least one (RULE-3)
 * @param selected     whether the customer's command covers this row (RULE-9, RULE-10)
 * @param availability what element 06 decided, stored as data
 * @param lastKnown    the offer as it was last read successfully (RULE-8)
 * @param readAt       when the observation this row carries was obtained; it moves with every applied observation,
 *                     including the one that keeps {@code lastKnown} (A4), so an answer older than a failed read is still
 *                     discarded (EVT-3). A document written before the row carried it rehydrates as {@link Instant#EPOCH},
 *                     the oldest possible read, so the next observation always applies — the same tolerance {@link Money}
 *                     states for a {@code kind} it did not always store
 */
record BasketItem(
        String offerId,
        String seller,
        int quantity,
        boolean selected,
        Availability availability,
        LastKnown lastKnown,
        Instant readAt) {

    /**
     * The offer as it was last read: what the row renders when the offer itself can no longer be read, and the prices the
     * handover crosses (RULE-8, HAND-5).
     *
     * @param title        the product title, one line in the row
     * @param picture      the one picture the row shows
     * @param currentPrice the offer's current price, one unit
     * @param discount     the offer's flattened discount of kind {@code discount}, zero when it carries none (D22)
     */
    record LastKnown(String title, String picture, Money currentPrice, Money discount) {

        LastKnown {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(picture, "picture");
            Objects.requireNonNull(currentPrice, "currentPrice");
            Objects.requireNonNull(discount, "discount");
            ensurePricesAreInOneCurrency(currentPrice, discount);
        }

        private static void ensurePricesAreInOneCurrency(Money currentPrice, Money discount) {
            if (!currentPrice.currency().equals(discount.currency())) {
                throw new ShoppingException(ShoppingException.Error.CURRENCY_MISMATCH,
                        discount.currency(), currentPrice.currency());
            }
        }
    }

    BasketItem {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(lastKnown, "lastKnown");
        readAt = readAt == null ? Instant.EPOCH : readAt;
        if (quantity < 1) {
            throw new ShoppingException(ShoppingException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
    }

    /** The currency the row is priced in — one per basket, so an item in another one is refused (element 03 RULE-2). */
    String currency() {
        return lastKnown.currentPrice().currency();
    }

    /** This row with {@code more} units added, selected: a repeated add merges and selects the row (D19). */
    BasketItem merging(int more) {
        return withQuantity(grownBy(more)).withSelection(true);
    }

    /** This row at another quantity — a change produces a new row, never an edited one (element 03 RULE-1). */
    BasketItem withQuantity(int quantity) {
        return new BasketItem(offerId, seller, quantity, selected, availability, lastKnown, readAt);
    }

    /** This row selected or deselected — the only thing a selection command changes (RULE-10). */
    BasketItem withSelection(boolean selected) {
        return new BasketItem(offerId, seller, quantity, selected, availability, lastKnown, readAt);
    }

    /**
     * This row after one offer observation, or this row unchanged when the observation is older than the one it already
     * carries (EVT-3, DoD21).
     *
     * <p>What is re-decided depends on what was answered. An observation that describes the offer decides its availability
     * again from itself, never from this row — that is what makes a failed read fail closed instead of keeping the
     * {@code available} it replaced (A4, D26) — and replaces every field it names, keeping the fields it left out (element 08
     * §Fields consumed) and regrouping the row under the seller it reports (RULE-7). A partial observation, which is a price
     * or description change, refreshes the content and leaves the availability alone, because it answered nothing about it
     * (R13, element 10 §Events consumed). An observation of the same instant is applied: it replaces the row with an equal one,
     * which is how a duplicate delivery costs nothing (EVT-4, DoD20).
     *
     * <p>An answer in another currency keeps the prices this row already holds and marks it stale (D63): element 03 RULE-2 makes
     * conversion impossible, so the row cannot move into that currency, and the flag is how it says that what it shows is
     * last-known. What D63 freezes is the prices, not the rest of the answer: the availability is still decided from that
     * answer's own state and stock, and the row is still regrouped under the seller it reports — a seller is a shipment, not a
     * price.
     *
     * @param command the observation, already mapped from the port's read record
     * @return the refreshed row, or this row when the observation is still older than {@link #readAt}
     */
    BasketItem applying(OfferObservationCommand command) {
        Objects.requireNonNull(command, "command");
        return command.readAt().isBefore(readAt) ? this : observedAs(command);
    }

    /**
     * Whether this row says exactly what {@code other} says, the read instant aside — what {@code revision} counts, because
     * a newer read of unchanged content is freshness and not a mutation of the customer's basket (element 02 §revision).
     *
     * @param other the row it is compared with
     * @return true when every field but {@link #readAt} is equal
     */
    boolean saysTheSameAs(BasketItem other) {
        Objects.requireNonNull(other, "other");
        return offerId.equals(other.offerId) && seller.equals(other.seller) && quantity == other.quantity
                && selected == other.selected && availability.equals(other.availability)
                && lastKnown.equals(other.lastKnown);
    }

    private BasketItem observedAs(OfferObservationCommand command) {
        return switch (command) {
            case OfferObservationCommand.Answered answered -> observedAs(answered);
            case OfferObservationCommand.Unanswered unanswered -> refreshed(lastKnown,
                    OfferAvailabilityDecision.decide(unanswered.observation()), seller, unanswered.readAt());
            case OfferObservationCommand.Refreshed repriced -> refreshed(repriced.snapshot().filledWith(lastKnown),
                    availability, seller, repriced.readAt());
        };
    }

    private BasketItem observedAs(OfferObservationCommand.Answered answered) {
        Availability decided = OfferAvailabilityDecision.decide(answered.observation());
        return answered.snapshot().pricedInAnotherCurrencyThan(lastKnown)
                ? refreshed(lastKnown, pricedAsLastKnown(decided), answered.seller(), answered.readAt())
                : refreshed(answered.snapshot().filledWith(lastKnown), decided, answered.seller(), answered.readAt());
    }

    private static Availability pricedAsLastKnown(Availability decided) {
        return new Availability(decided.status(), decided.reason(), true);
    }

    private BasketItem refreshed(LastKnown snapshot, Availability decided, String groupedUnder, Instant obtainedAt) {
        return new BasketItem(offerId, groupedUnder, quantity, selected, decided, snapshot, obtainedAt);
    }

    private int grownBy(int more) {
        try {
            return Math.addExact(quantity, more);
        } catch (ArithmeticException beyondTheRange) {
            throw new ShoppingException(ShoppingException.Error.QUANTITY_OUT_OF_RANGE, beyondTheRange, quantity, more);
        }
    }
}
