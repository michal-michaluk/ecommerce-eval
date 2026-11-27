package shop.shopping.basket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import shop.shopping.IntegrationTest;
import shop.shopping.JsonAssert;
import shop.shopping.RecordedEvents;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.CUSTOMER_ID;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.PLN;
import static shop.shopping.basket.BasketFixture.SECOND_OFFER;
import static shop.shopping.basket.BasketFixture.aBasketFor;
import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.kernel.HandoverFixture.theTotalsOfOneItem;
import static shop.shopping.basket.BasketFixture.aFreshCustomer;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.anArchivedBasketFor;
import static shop.shopping.basket.BasketFixture.anEmptyBasket;
import static shop.shopping.basket.BasketFixture.anotherBasketId;

/**
 * The basket's document adapter: one JSONB row per basket with the aggregate round-tripping through its snapshot (D41), the
 * schema refusing a second active basket for one customer (D42, RULE-1), a lost update being refused instead of silently
 * applied, and two requests that both found no basket ending with exactly one (DoD22).
 */
@IntegrationTest
class DocumentBasketRepositoryTest {

    @Autowired
    DocumentBasketRepository baskets;

    @Autowired
    BasketService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RecordedEvents recorded;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void storesTheAggregateAndReadsItBackWithItsItems() {
        BasketEditor basket = aBasketWith(aFirstItem(), aSecondItem());
        basket.changeQuantity(FIRST_OFFER, 3);

        baskets.save(basket);

        assertThat(baskets.get(BASKET_ID.value())).get().satisfies(stored -> {
            Basket snapshot = stored.toSnapshot();
            assertThat(snapshot.basketId()).isEqualTo(BASKET_ID);
            assertThat(snapshot.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(snapshot.currency()).isEqualTo(PLN);
            assertThat(snapshot.revision()).isEqualTo(1);
            assertThat(snapshot.items()).extracting(BasketItem::offerId).containsExactly(FIRST_OFFER, SECOND_OFFER);
            assertThat(snapshot.items().get(0).quantity()).isEqualTo(3);
        });
    }

    @Test
    void storesTheSnapshotAsTheOneJsonbDocumentItIs() {
        BasketEditor basket = anEmptyBasket();
        basket.addToBasket(aFirstItem());
        baskets.save(basket);

        String document = jdbc.queryForObject("select snapshot from basket where basket_id = ?", String.class,
                BASKET_ID.value());

        JsonAssert.assertThat(document).isExactlyLike("""
                {
                  "basketId": {"value": "B-7f3a"},
                  "customerId": {"value": "C-42"},
                  "currency": "PLN",
                  "state": "active",
                  "items": [{
                    "offerId": "OF-1001",
                    "seller": "UGREEN_POLSKA",
                    "quantity": 1,
                    "selected": true,
                    "availability": {"status": "available", "reason": "stillOnSale", "stale": false},
                    "lastKnown": {
                      "title": "UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W",
                      "picture": "img-1001",
                      "currentPrice": {"amount": 99890, "currency": "PLN", "kind": "charge"},
                      "discount": {"amount": 0, "currency": "PLN", "kind": "discount"}
                    },
                    "readAt": "2026-11-26T10:00:00Z"
                  }],
                  "revision": 1,
                  "lastHandover": null
                }""");
    }

    @Test
    void findsTheActiveBasketOfACustomerAndNothingElse() {
        baskets.save(aBasketWith(aFirstItem()));

        assertThat(baskets.findActiveFor(CUSTOMER_ID)).get()
                .satisfies(found -> assertThat(found.toSnapshot().basketId()).isEqualTo(BASKET_ID));
        assertThat(baskets.findActiveFor(aFreshCustomer())).isEmpty();
    }

    @Test
    void refusesASecondActiveBasketForTheSameCustomer() {
        baskets.save(aBasketWith(aFirstItem()));

        assertThatThrownBy(() -> baskets.save(aBasketFor(anotherBasketId(), CUSTOMER_ID)))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CONCURRENT_MODIFICATION));
    }

    @Test
    void allowsAnyNumberOfArchivedBasketsOfOneCustomer() {
        baskets.save(anEmptyBasket());

        baskets.save(anArchivedBasketFor(anotherBasketId(), CUSTOMER_ID, aFirstItem()));
        baskets.save(anArchivedBasketFor(BasketId.minted(), CUSTOMER_ID, aSecondItem()));

        assertThat(countOf("select count(*) from basket where customer_id = ?", CUSTOMER_ID.value())).isEqualTo(3);
    }

    @Test
    void readsTheHandoverIdentityBackOutOfTheStoredDocument() {
        BasketEditor basket = anEmptyBasket();
        basket.addToBasket(aFirstItem());
        HandoverDraft first = basket.handOver(NOW, theTotalsOfOneItem());
        baskets.save(basket);
        entityManager.flush();
        entityManager.clear();

        HandoverDraft repeated = baskets.get(BASKET_ID.value()).orElseThrow().handOver(NOW, theTotalsOfOneItem());

        assertThat(first.identity().createdAt()).isEqualTo(NOW);
        assertThat(repeated.repeated()).isTrue();
        assertThat(repeated.identity()).isEqualTo(first.identity());
        assertThat(repeated.selection()).isEqualTo(first.selection());
    }

    @Test
    void publishesTheEventsOfOneWriteOnce() {
        BasketEditor basket = anEmptyBasket();
        basket.addToBasket(aFirstItem());

        baskets.save(basket);

        assertThat(recorded.of(DomainEvent.AddedToBasket.class)).hasSize(1);
        assertThat(basket.events).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesAWriteThatLostTheRace() throws Exception {
        baskets.save(aBasketWith(aFirstItem()));
        CountDownLatch bothLoaded = new CountDownLatch(2);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        inTwoThreads(() -> {
            quantityChangedAfterBothLoaded(bothLoaded);
            return null;
        }, failures);

        assertThat(failures).singleElement().isInstanceOfSatisfying(ShoppingException.class,
                refused -> assertThat(refused.error()).isEqualTo(ShoppingException.Error.CONCURRENT_MODIFICATION));
        assertThat(baskets.get(BASKET_ID.value()).orElseThrow().toSnapshot().revision()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createsExactlyOneActiveBasketWhenTwoRequestsBothFoundNone() throws Exception {
        CustomerId customer = aFreshCustomer();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        List<Basket> answered = inTwoThreads(() -> service.basketOf(customer), failures);

        assertThat(failures).isEmpty();
        assertThat(answered).extracting(Basket::basketId).hasSize(2)
                .containsOnly(answered.get(0).basketId());
        assertThat(countOf("select count(*) from basket where customer_id = ? and active = true",
                customer.value())).isEqualTo(1);
    }

    private void quantityChangedAfterBothLoaded(CountDownLatch bothLoaded) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            BasketEditor basket = baskets.get(BASKET_ID.value()).orElseThrow();
            basket.changeQuantity(FIRST_OFFER, 2);
            bothLoaded.countDown();
            awaitQuietly(bothLoaded);
            baskets.save(basket);
        });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the race was interrupted", interrupted);
        }
    }

    private static <T> List<T> inTwoThreads(Supplier<T> work, List<Throwable> failures) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        List<T> answers = new CopyOnWriteArrayList<>();
        try {
            Future<?> first = threads.submit(() -> captured(work, answers, failures));
            Future<?> second = threads.submit(() -> captured(work, answers, failures));
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }
        return answers;
    }

    private static <T> T captured(Supplier<T> work, List<T> answers, List<Throwable> failures) {
        try {
            T answer = work.get();
            answers.add(answer);
            return answer;
        } catch (RuntimeException failure) {
            failures.add(failure);
            return null;
        }
    }

    private int countOf(String query, String argument) {
        return jdbc.queryForObject(query, Integer.class, argument);
    }
}
