package shop.shopping.basket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import shop.shopping.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.aBasketOf;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aFreshCustomer;

/**
 * The handover through the stored document: {@code revision} and {@code lastHandover} are the only derived fields the basket
 * keeps of it (D41), so they are the only thing that can make a repeat answer the same handover after the aggregate was
 * rehydrated — which is what a second request does, and what a restarted pod does (DoD28, DoD30).
 *
 * <p>Every path here flushes and clears the persistence context first, so the aggregate under test is rebuilt from the JSONB
 * column and not from the instance the previous call left in memory. Each test uses its own basket identity because the
 * checkout slice holds the revision it accepted per basket in memory, outside the test's transaction: a shared identity would
 * make the tests order-dependent — which is why the order is pinned to the one that would break first if they shared it.
 */
@IntegrationTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class BasketHandoverDocumentTest {

    @Autowired
    DocumentBasketRepository baskets;

    @Autowired
    BasketService service;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void answersTheSameHandoverAfterTheBasketWasRehydrated() {
        BasketId basketId = aStoredBasket();

        HandoverAnswer first = service.handOver(basketId, Channel.standard);
        entityManager.flush();
        entityManager.clear();
        HandoverAnswer repeated = service.handOver(basketId, Channel.standard);

        assertThat(repeated).isEqualTo(first);
        assertThat(repeated.revision()).isEqualTo(first.revision());
        assertThat(baskets.get(basketId.value()).orElseThrow().toSnapshot().lastHandover()).isNotNull();
    }

    @Test
    void answersANewRevisionAfterTheRehydratedBasketChanged() {
        BasketId basketId = aStoredBasket();

        HandoverAnswer first = service.handOver(basketId, Channel.standard);
        entityManager.flush();
        entityManager.clear();
        service.changeQuantity(basketId, FIRST_OFFER, 2);
        HandoverAnswer second = service.handOver(basketId, Channel.standard);

        assertThat(second.revision()).isGreaterThan(first.revision());
    }

    private BasketId aStoredBasket() {
        BasketId basketId = BasketId.minted();
        baskets.save(aBasketOf(basketId, aFreshCustomer(), aFirstItem()));
        return basketId;
    }
}
