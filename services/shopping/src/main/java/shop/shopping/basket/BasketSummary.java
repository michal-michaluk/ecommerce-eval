package shop.shopping.basket;

import java.util.Objects;

/**
 * One row of {@code GET /baskets} — step 1 of {@code prototypes/basket-api.prove.md}, and the whole of that route's read
 * model: which basket, whether it is still editable and the one currency it holds.
 *
 * <p>It is deliberately not a summary of the basket's content. A list of a customer's baskets is a list of identities with a
 * state; the content, the totals and the estimate belong to the render of one basket (element 07 §Read shape, D10) — and copying
 * them here would make this row a second place selection and availability could disagree with the aggregate (RULE-10).
 *
 * @param basketId the identity every path carries
 * @param state    whether the basket is still editable
 * @param currency the one currency the basket holds
 */
record BasketSummary(String basketId, BasketState state, String currency) {

    BasketSummary {
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currency, "currency");
    }

    static BasketSummary of(Basket basket) {
        return new BasketSummary(basket.basketId().value(), basket.state(), basket.currency());
    }
}
