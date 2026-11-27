package shop.shopping.basket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@code GET /baskets} as a read model: one row per basket, holding the summary the route serves, keyed by the customer the
 * basket belongs to.
 *
 * <p>It is the one read model of element 07, and the one write behind a read (element 07 §Recorded deviations, D29/D42): a
 * customer always has at least one active basket, so this projection <em>delegates the creation to the basket service and then
 * answers from its own row</em>. It owns nothing else — the render of one basket is the write model's, because a projection fed
 * by the published events could not serve it: {@code select} emits no event at all (RULE-10), so a projected {@code selected}
 * flag would be stale the moment the customer unticked a box.
 *
 * <p>For the same reason this row is refreshed from the aggregate's own snapshot on the read that creates or finds the basket,
 * instead of from the domain events: the four events the aggregate publishes ({@code AddedToBasket},
 * {@code BasketQuantityChanged}, {@code RemovedFromBasket}, {@code GoToCheckout}) carry no customer, no currency and no state,
 * so no handler could build a row this route can answer. What the row holds is what those events could never disagree about —
 * the basket's identity, its state and its currency — and it is written by the one call that asks the write model anyway, and
 * only when it says something the row does not already say.
 *
 * <p>What the row cannot follow is a basket leaving the active set: a {@code GET /baskets} refreshes the row of the basket the
 * service answered for, so a basket archived by another route keeps {@code active} until this route reads that customer again —
 * and it then answers the new active basket, not the archived one. Nothing archives a basket in this increment (HAND-3, D4), so
 * the state is unreachable; a completed checkout that archives one must refresh this row, which is the projection's own table.
 */
@Component
@Transactional
class BasketsProjection {

    private final BasketService baskets;
    private final SummaryRows summaries;

    BasketsProjection(BasketService baskets, SummaryRows summaries) {
        this.baskets = baskets;
        this.summaries = summaries;
    }

    /**
     * The customer's active baskets — never an empty list, because asking creates the one the customer always owns.
     *
     * @param customerId the identity the token carries
     * @return one row per active basket, the created one included
     */
    List<BasketSummary> summariesOf(CustomerId customerId) {
        recorded(baskets.basketOf(customerId));
        return summaries.findAllByCustomerIdAndActive(customerId.value(), true).stream()
                .map(SummaryEntity::summary)
                .toList();
    }

    private void recorded(Basket basket) {
        SummaryEntity row = summaries.findById(basket.basketId().value())
                .orElseGet(() -> new SummaryEntity(basket.basketId().value()));
        if (!row.saysTheSameAs(basket)) {
            summaries.save(row.storedAs(basket));
        }
    }

    interface SummaryRows extends JpaRepository<SummaryEntity, String> {

        List<SummaryEntity> findAllByCustomerIdAndActive(String customerId, boolean active);
    }

    /**
     * One basket's row: the two columns a query filters on, plus the read model the route serves as a JSONB column, so the
     * derivation runs once per write instead of once per request.
     */
    @Entity
    @DynamicUpdate
    @Table(name = "basket_summary")
    static class SummaryEntity {

        @Id
        @Column(name = "basket_id")
        private String basketId;
        @Version
        private long version;
        @Column(name = "customer_id")
        private String customerId;
        @Column(name = "active")
        private boolean active;
        @JdbcTypeCode(SqlTypes.JSON)
        private BasketSummary summary;

        protected SummaryEntity() {
        }

        SummaryEntity(String basketId) {
            this.basketId = basketId;
        }

        BasketSummary summary() {
            return summary;
        }

        long version() {
            return version;
        }

        boolean saysTheSameAs(Basket basket) {
            return basket.customerId().value().equals(customerId) && active == basket.isActive()
                    && BasketSummary.of(basket).equals(summary);
        }

        SummaryEntity storedAs(Basket basket) {
            this.customerId = basket.customerId().value();
            this.active = basket.isActive();
            this.summary = BasketSummary.of(basket);
            return this;
        }
    }
}
