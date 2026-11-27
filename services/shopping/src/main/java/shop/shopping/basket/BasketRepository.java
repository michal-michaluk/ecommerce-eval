package shop.shopping.basket;

import java.util.Optional;

/**
 * The one secondary port of the basket: the adapter rehydrates a {@link BasketEditor} and saves it back, whichever storage
 * shape it uses ({@code persistence-adapters.md}).
 *
 * <p>{@link #findActiveFor} is the single extra read the auto-creation path needs: a customer always has one active basket,
 * so the service asks which one exists before deciding to create it (RULE-1, D42).
 */
interface BasketRepository {

    Optional<BasketEditor> get(String basketId);

    Optional<BasketEditor> findActiveFor(CustomerId customerId);

    void save(BasketEditor basket);
}
