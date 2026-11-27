package shop.shopping.basket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import shop.shopping.IntegrationTest;
import shop.shopping.JsonAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketFixture.aFreshCustomer;

/**
 * The read model behind {@code GET /baskets}, driven through its own port: a customer who has no basket gets the one they
 * always own (D29, D42, element 07 §Recorded deviations), a second read answers the same row, and two customers never share one.
 *
 * <p>The row is written from the aggregate's own snapshot by the call that creates or finds the basket, because the four events
 * the aggregate publishes carry no customer, no currency and no state — no handler could build a row this route can answer.
 * What the row holds is what a projection over those events could never disagree about: the identity, the state, the currency.
 */
@IntegrationTest
class BasketsProjectionTest {

    @Autowired
    BasketsProjection projection;
    @Autowired
    BasketsProjection.SummaryRows summaries;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void createsTheBasketTheCustomerHasNoneOfAndAnswersItsRow() {
        CustomerId customer = aFreshCustomer();

        assertThat(projection.summariesOf(customer)).singleElement()
                .satisfies(summary -> assertThat(summary.basketId()).startsWith("B-"));
        JsonAssert.assertThat(projection.summariesOf(customer)).isExactlyLike("""
                [ { "basketId": "%s", "state": "active", "currency": "PLN" } ]
                """, projection.summariesOf(customer).get(0).basketId());
    }

    @Test
    void answersTheSameBasketOnEveryLaterRead() {
        CustomerId customer = aFreshCustomer();

        String first = projection.summariesOf(customer).get(0).basketId();
        String second = projection.summariesOf(customer).get(0).basketId();

        assertThat(second).isEqualTo(first);
        assertThat(projection.summariesOf(customer)).hasSize(1);
    }

    @Test
    void keepsOneCustomersRowFromAnsweringAnother() {
        CustomerId first = aFreshCustomer();
        CustomerId second = aFreshCustomer();

        assertThat(projection.summariesOf(first).get(0).basketId())
                .isNotEqualTo(projection.summariesOf(second).get(0).basketId());
    }

    @Test
    void rewritesTheRowOnlyWhenTheBasketSaysSomethingTheRowDoesNot() {
        CustomerId customer = aFreshCustomer();
        String basketId = projection.summariesOf(customer).get(0).basketId();
        long writtenOnce = versionOf(basketId);

        projection.summariesOf(customer);

        assertThat(versionOf(basketId)).isEqualTo(writtenOnce);
    }

    /**
     * The row a read has to correct: a basket whose stored row contradicts it is rewritten, and the rewrite is what bumps
     * {@code version}. Nothing archives a basket in this increment (HAND-3, D4), so the contradiction is planted in the row
     * itself — which is also the only way to tell a version apart from the constant zero a broken accessor would answer.
     */
    @Test
    void rewritesARowThatContradictsTheBasketAndBumpsItsVersion() {
        CustomerId customer = aFreshCustomer();
        String basketId = projection.summariesOf(customer).get(0).basketId();
        long writtenOnce = versionOf(basketId);
        entityManager.createNativeQuery("update basket_summary set active = false where basket_id = :basketId")
                .setParameter("basketId", basketId)
                .executeUpdate();
        entityManager.clear();

        projection.summariesOf(customer);

        assertThat(versionOf(basketId)).isGreaterThan(writtenOnce);
    }

    private long versionOf(String basketId) {
        return summaries.findById(basketId).orElseThrow().version();
    }}
