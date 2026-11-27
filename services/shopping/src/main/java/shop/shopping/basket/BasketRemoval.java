package shop.shopping.basket;

import java.util.Objects;

/**
 * Which rows a removal command takes — element 07's {@code scope} of {@code PATCH /baskets/{basketId}/items}, in the
 * aggregate's own terms: one named offer, the current selection, or the whole basket (element 02 §Behaviour, D18).
 *
 * <p>It exists because the three removals are three different operations and the route carries one body: a scope names the
 * rows, {@link #removeFrom} performs it, and neither the adapter nor the service has to know which of the three it is. What it
 * never expresses is a quantity — this route only ever removes (D28).
 */
enum BasketRemoval {

    /** The one offer the body names. */
    item,

    /** Every row the customer has selected — the screen's {@code USUŃ} over the selection (D18). */
    selection,

    /** Every row, leaving an empty active basket (Q27). */
    all;

    /**
     * Removes this scope from the basket.
     *
     * @param basket  the aggregate, which decides whether the command is allowed
     * @param offerId the offer the body named; absent for the two scopes that name none
     */
    void removeFrom(BasketEditor basket, String offerId) {
        Objects.requireNonNull(basket, "basket");
        switch (this) {
            case item -> basket.removeItem(Objects.requireNonNull(offerId, "offerId"));
            case selection -> basket.removeSelectedItems();
            case all -> basket.removeEverything();
        }
    }
}
