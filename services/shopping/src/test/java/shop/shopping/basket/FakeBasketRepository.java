package shop.shopping.basket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import shop.shopping.kernel.ShoppingException;

/**
 * The {@link BasketRepository} port backed by maps: the same find-or-create lifecycle the adapter has, without a database,
 * so a unit test asserts the use case and not the storage. It drains the aggregate's buffer on save the way the adapter
 * does, so the events a test sees are the ones that were published.
 *
 * <p>{@link #loseTheCreateTo} reproduces what the schema does under a race: the customer's basket appears between the read
 * and the write, and the write is refused — the one branch of the service no single-threaded test would reach.
 */
class FakeBasketRepository implements BasketRepository {

    final Map<String, BasketEditor> baskets = new LinkedHashMap<>();
    final List<String> saved = new ArrayList<>();
    final List<DomainEvent> emitted = new ArrayList<>();
    private BasketEditor winner;

    @Override
    public Optional<BasketEditor> get(String basketId) {
        return Optional.ofNullable(baskets.get(basketId));
    }

    @Override
    public Optional<BasketEditor> findActiveFor(CustomerId customerId) {
        return baskets.values().stream()
                .filter(basket -> basket.toSnapshot().customerId().equals(customerId))
                .filter(basket -> basket.toSnapshot().isActive())
                .findFirst();
    }

    @Override
    public void save(BasketEditor basket) {
        if (winner != null) {
            baskets.put(winner.basketId.value(), winner);
            winner = null;
            throw new ShoppingException(ShoppingException.Error.CONCURRENT_MODIFICATION, basket.basketId.value());
        }
        emitted.addAll(basket.events);
        basket.events.clear();
        baskets.put(basket.basketId.value(), basket);
        saved.add(basket.basketId.value());
    }

    void loseTheCreateTo(BasketEditor anotherRequest) {
        winner = anotherRequest;
    }

    BasketEditor stored(BasketId basketId) {
        return baskets.get(basketId.value());
    }
}
