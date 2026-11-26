package shop.shopping.basket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

/**
 * The basket's primary port: one method per operation of element 02, each loading the aggregate by its identity, letting the
 * aggregate decide, and storing it back.
 *
 * <p>It holds no rule of its own — merge, quantity, blocking and handover identity belong to {@link BasketEditor}. What it
 * does own is the one write behind a read: a customer always has an active basket, so asking for it creates it when none
 * exists (RULE-1, D42, element 07 §Recorded deviations).
 *
 * <p>Creation is the one method with no ambient transaction: the attempt may be refused by the schema's partial unique
 * index, and the re-read of the winner needs a transaction of its own, because a refused statement leaves the surrounding
 * one unusable. With no transaction to inherit, each repository call is its own — and the loser of a creation race answers
 * the basket the winner stored, so the customer ends up with exactly one.
 */
@Service
@Transactional
class BasketService {

    private final BasketRepository baskets;
    private final BasketCurrency currency;
    private final Clock clock;

    BasketService(BasketRepository baskets, BasketCurrency currency, Clock clock) {
        this.baskets = baskets;
        this.currency = currency;
        this.clock = clock;
    }

    /**
     * The customer's active basket, created when the customer has none (RULE-1, D42, Q27).
     *
     * @param customerId the identity the token carries
     * @return the basket as the read side serves it
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    Basket basketOf(CustomerId customerId) {
        Objects.requireNonNull(customerId, "customerId");
        return basketFor(customerId).toSnapshot();
    }

    /**
     * Reads one basket by its identity.
     *
     * @param basketId the identity the path carries
     * @return the basket as the read side serves it
     */
    @Transactional(readOnly = true)
    Basket basket(BasketId basketId) {
        return editor(basketId).toSnapshot();
    }

    Basket addToBasket(BasketId basketId, BasketItem item) {
        BasketEditor basket = editor(basketId);
        basket.addToBasket(item);
        return store(basket);
    }

    Basket changeQuantity(BasketId basketId, String offerId, int quantity) {
        BasketEditor basket = editor(basketId);
        basket.changeQuantity(offerId, quantity);
        return store(basket);
    }

    Basket removeItem(BasketId basketId, String offerId) {
        BasketEditor basket = editor(basketId);
        basket.removeItem(offerId);
        return store(basket);
    }

    Basket removeSelectedItems(BasketId basketId) {
        BasketEditor basket = editor(basketId);
        basket.removeSelectedItems();
        return store(basket);
    }

    Basket removeEverything(BasketId basketId) {
        BasketEditor basket = editor(basketId);
        basket.removeEverything();
        return store(basket);
    }

    Basket select(BasketId basketId, BasketSelection selection, boolean selected) {
        BasketEditor basket = editor(basketId);
        basket.select(selection, selected);
        return store(basket);
    }

    /**
     * Hands the selection over and answers the payload checkout receives; the basket stays active and editable (HAND-6).
     *
     * @param basketId the basket the path carries, which is also the handover's identity (D32)
     * @return the payload, carrying the identity of the content that crossed
     */
    Handover handOver(BasketId basketId) {
        BasketEditor basket = editor(basketId);
        Handover handover = basket.handOver(Instant.now(clock));
        baskets.save(basket);
        return handover;
    }

    private BasketEditor basketFor(CustomerId customerId) {
        return baskets.findActiveFor(customerId).orElseGet(() -> open(customerId));
    }

    private BasketEditor open(CustomerId customerId) {
        BasketEditor opened = BasketEditor.openBasketFor(BasketId.minted(), customerId, currency.code());
        try {
            baskets.save(opened);
            return opened;
        } catch (ShoppingException taken) {
            return baskets.findActiveFor(customerId).orElseThrow(() -> taken);
        }
    }

    private BasketEditor editor(BasketId basketId) {
        Objects.requireNonNull(basketId, "basketId");
        return baskets.get(basketId.value()).orElseThrow(
                () -> new ShoppingException(ShoppingException.Error.BASKET_NOT_FOUND, basketId.value()));
    }

    private Basket store(BasketEditor basket) {
        baskets.save(basket);
        return basket.toSnapshot();
    }
}
