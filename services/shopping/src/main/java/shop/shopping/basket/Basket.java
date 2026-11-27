package shop.shopping.basket;

import java.util.List;


/**
 * The basket as it is read and as it is stored: the customer's live configuration of offers, its revision and the identity
 * of the handover it last produced (element 02, D41).
 *
 * <p>It carries no behaviour — the aggregate decides, the snapshot answers. Selection and availability are part of the
 * state, not a history: no event describes them (RULE-10).
 *
 * @param basketId     the identity every API path carries (RULE-1)
 * @param customerId   the customer the basket belongs to, from the token
 * @param currency     the one currency the basket holds (element 03 RULE-2)
 * @param state        whether the basket is still editable
 * @param items        the rows, in the order they were added
 * @param revision     monotonic per mutation, part of the handover (D35)
 * @param lastHandover the identity of the last handover, absent until the basket was handed over
 */
record Basket(
        BasketId basketId,
        CustomerId customerId,
        String currency,
        BasketState state,
        List<BasketItem> items,
        long revision,
        LastHandover lastHandover) {

    Basket {
        items = List.copyOf(items);
    }

    /** Whether the basket is still editable — the state guard of every command. */
    boolean isActive() {
        return state == BasketState.active;
    }
}
