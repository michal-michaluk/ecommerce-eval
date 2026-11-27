package shop.shopping.basket;

import java.util.Objects;

/**
 * The rows one selection command acts on — the {@code scope} of element 07's {@code PATCH …/selection}: one item, every
 * item of one shipment, or the whole basket (element 02 RULE-7, RULE-9).
 *
 * <p>It names rows and decides nothing: whether a row is selected is a flag on the item, and deselecting everything leaves
 * a valid basket with an empty selection (RULE-9, RULE-10).
 */
sealed interface BasketSelection {

    /** Whether this scope names the given row. */
    boolean covers(BasketItem item);

    /** One row, addressed by its offer. */
    record Item(String offerId) implements BasketSelection {

        public Item {
            Objects.requireNonNull(offerId, "offerId");
        }

        @Override
        public boolean covers(BasketItem item) {
            return item.offerId().equals(offerId);
        }
    }

    /** Every row of one seller — the shipment header's checkbox (RULE-7). */
    record Shipment(String seller) implements BasketSelection {

        public Shipment {
            Objects.requireNonNull(seller, "seller");
        }

        @Override
        public boolean covers(BasketItem item) {
            return item.seller().equals(seller);
        }
    }

    /** Every row of the basket — the whole-basket checkbox of the screen. */
    record All() implements BasketSelection {

        @Override
        public boolean covers(BasketItem item) {
            return true;
        }
    }
}
