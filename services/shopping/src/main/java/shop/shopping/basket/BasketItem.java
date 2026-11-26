package shop.shopping.basket;

import java.util.Objects;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;
import shop.shopping.kernel.ShoppingException;

/**
 * One offer in a basket: which offer, whose shipment it belongs to, how many, whether the customer selected it, and what
 * element 06 last decided about it (element 02 RULE-4, RULE-7).
 *
 * <p>The last-known snapshot is kept whatever happens to the offer, so an erased offer still renders as a blocked row
 * instead of disappearing (RULE-5, RULE-8). The snapshot is never an input to a calculation: it is what the row shows, and
 * the price the handover crosses is the offer's current one.
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
 */
record BasketItem(
        String offerId,
        String seller,
        int quantity,
        boolean selected,
        Availability availability,
        LastKnown lastKnown) {

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
        return new BasketItem(offerId, seller, quantity, selected, availability, lastKnown);
    }

    /** This row selected or deselected — the only thing a selection command changes (RULE-10). */
    BasketItem withSelection(boolean selected) {
        return new BasketItem(offerId, seller, quantity, selected, availability, lastKnown);
    }

    private int grownBy(int more) {
        try {
            return Math.addExact(quantity, more);
        } catch (ArithmeticException beyondTheRange) {
            throw new ShoppingException(ShoppingException.Error.QUANTITY_OUT_OF_RANGE, beyondTheRange, quantity, more);
        }
    }
}
