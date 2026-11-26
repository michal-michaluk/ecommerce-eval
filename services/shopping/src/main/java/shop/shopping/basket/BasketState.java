package shop.shopping.basket;

/**
 * Whether the customer can still change the basket. A basket stays {@link #active} while it is editable; {@link #archived}
 * is the terminal state a completed checkout leaves behind, and the checkout slice owns that transition (element 09).
 */
enum BasketState {
    active,
    archived
}
