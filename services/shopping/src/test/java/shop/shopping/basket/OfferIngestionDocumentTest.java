package shop.shopping.basket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import shop.shopping.IntegrationTest;
import shop.shopping.kernel.Availability;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.OfferCatalogFixture.A_LATER_READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.A_CHARGED_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.aCommandOf;
import static shop.shopping.basket.OfferCatalogFixture.A_DISCOUNTED_AMOUNT;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOnSaleWithStockReadAt;

/**
 * The application path through the stored document: an observation changes a row, the row keeps the price it is sold at and
 * the instant it answered, and the snapshot round-trips through the JSONB column unchanged (D41, D53).
 *
 * <p>This is the one place the observation cannot be proved by values in memory: {@code readAt} is a new component of
 * {@link BasketItem}, so a document written before it existed would fail to rehydrate — which is exactly what this test would
 * report.
 */
@IntegrationTest
class OfferIngestionDocumentTest {

    @Autowired
    DocumentBasketRepository baskets;

    @Autowired
    BasketService service;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void keepsTheObservationItsReadInstantAndItsPricesInTheStoredDocument() {
        baskets.save(aBasketWith(aFirstItem()));

        service.applyObservation(BASKET_ID, aCommandOf(anOfferOnSaleWithStockReadAt(0, A_LATER_READ_AT)));
        entityManager.flush();
        entityManager.clear();

        BasketItem stored = baskets.get(BASKET_ID.value()).orElseThrow().toSnapshot().items().get(0);

        assertThat(stored.readAt()).isEqualTo(A_LATER_READ_AT);
        assertThat(stored.availability()).isEqualTo(Availability.outOfStock());
        assertThat(stored.lastKnown().currentPrice()).isEqualTo(A_CHARGED_PRICE);
        assertThat(stored.lastKnown().discount()).isEqualTo(A_DISCOUNTED_AMOUNT);
    }
}
