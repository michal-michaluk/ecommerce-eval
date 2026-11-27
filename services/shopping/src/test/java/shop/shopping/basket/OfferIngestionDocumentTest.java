package shop.shopping.basket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import shop.shopping.IntegrationTest;
import shop.shopping.kernel.Availability;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aLastKnown;
import static shop.shopping.basket.OfferCatalogFixture.A_LATER_READ_AT;
import static shop.shopping.basket.OfferCatalogFixture.A_CHARGED_PRICE;
import static shop.shopping.basket.OfferCatalogFixture.aCommandOf;
import static shop.shopping.basket.OfferCatalogFixture.A_DISCOUNTED_AMOUNT;
import static shop.shopping.basket.OfferCatalogFixture.anOfferOnSaleWithStockReadAt;

/**
 * The application path through the stored document: an observation changes a row, the row keeps the price it is sold at and
 * the instant it answered, and the snapshot round-trips through the JSONB column unchanged (D41, D53).
 *
 * <p>This is the one place the observation cannot be proved by values in memory: {@code readAt} is a component the row did not
 * always carry (D59), so a document written before it existed has to rehydrate — which is exactly what the first test would
 * report if it did not. The restated document is read back through the column, never through the instance the write left in
 * the persistence context.
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

    @Test
    void rehydratesAStoredRowThatCarriesNoReadInstantAsTheOldestOne() {
        baskets.save(aBasketWith(aFirstItem()));
        entityManager.flush();
        withoutTheReadInstantOfTheFirstRow();

        BasketItem stored = baskets.get(BASKET_ID.value()).orElseThrow().toSnapshot().items().get(0);

        assertThat(stored.readAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void keepsTheLastKnownPricesOfAForeignCurrencyObservationInTheStoredDocument() {
        baskets.save(aBasketWith(aFirstItem()));

        service.applyObservation(BASKET_ID, aCommandOf(OfferCatalogFixture.anOfferPricedIn("EUR")));
        entityManager.flush();
        entityManager.clear();

        BasketItem stored = baskets.get(BASKET_ID.value()).orElseThrow().toSnapshot().items().get(0);

        assertThat(stored.lastKnown()).isEqualTo(aLastKnown());
        assertThat(stored.availability()).isEqualTo(Availability.availableWithoutStockRead());
    }

    private void withoutTheReadInstantOfTheFirstRow() {
        entityManager.createNativeQuery("update basket set snapshot = snapshot #- '{items,0,readAt}'"
                        + " where basket_id = :basketId")
                .setParameter("basketId", BASKET_ID.value())
                .executeUpdate();
        entityManager.clear();
    }
}
