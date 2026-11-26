package shop.shopping.basket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import shop.shopping.kernel.ShoppingException;

/**
 * The {@link BasketRepository} document-snapshot adapter: one JSONB row per basket holding the aggregate's own snapshot, plus
 * the two columns a query reads without opening the document — the customer it belongs to and whether it is active (D42).
 *
 * <p>One active basket per customer is a rule of the schema, not of the code: the partial unique index on
 * {@code (customer_id) where active} refuses the second one, so two requests that both found no basket cannot both create
 * one. The loser of that race is translated into {@code CONCURRENT_MODIFICATION} and the service re-reads the winner,
 * which is why the write is flushed here instead of at commit — a lost update, or a lost race, is invisible to the caller
 * otherwise.
 *
 * <p>The adapter drains the aggregate's event buffer before writing, so a failed write cannot publish a fact twice, and
 * publishes every event once after the row is written ({@code persistence.drain-events},
 * {@code persistence.publish-after-write}).
 */
@Repository
class DocumentBasketRepository implements BasketRepository {

    private static final String ACTIVE_BASKET_INDEX = "basket_active_customer_idx";

    private final BasketRows baskets;
    private final ApplicationEventPublisher publisher;

    DocumentBasketRepository(BasketRows baskets, ApplicationEventPublisher publisher) {
        this.baskets = baskets;
        this.publisher = publisher;
    }

    @Override
    public Optional<BasketEditor> get(String basketId) {
        return baskets.findById(basketId).map(DocumentBasketRepository::rehydrate);
    }

    @Override
    public Optional<BasketEditor> findActiveFor(CustomerId customerId) {
        return baskets.findByCustomerIdAndActive(customerId.value(), true).map(DocumentBasketRepository::rehydrate);
    }

    @Override
    public void save(BasketEditor basket) {
        List<DomainEvent> emitted = eventsEmittedFrom(basket);
        write(basket.toSnapshot());
        emitted.forEach(event -> publisher.publishEvent(event));
    }

    /** Copies the aggregate's buffer and clears it before the write, so a failed save cannot publish a fact twice. */
    static List<DomainEvent> eventsEmittedFrom(BasketEditor basket) {
        List<DomainEvent> emitted = List.copyOf(basket.events);
        basket.events.clear();
        return emitted;
    }

    /** The aggregate rebuilt from its snapshot: the all-args constructor, and a mutable event buffer that starts empty. */
    private static BasketEditor rehydrate(BasketEntity entity) {
        Basket snapshot = entity.snapshot();
        return new BasketEditor(snapshot.basketId(), new ArrayList<>(), snapshot.customerId(), snapshot.currency(),
                new ArrayList<>(snapshot.items()), snapshot.state(), snapshot.revision(), snapshot.lastHandover());
    }

    private void write(Basket snapshot) {
        try {
            baskets.saveAndFlush(stored(snapshot));
        } catch (OptimisticLockingFailureException lost) {
            throw concurrentModification(snapshot, lost);
        } catch (DataIntegrityViolationException refused) {
            throw isSecondActiveBasket(refused) ? concurrentModification(snapshot, refused) : refused;
        }
    }

    private static ShoppingException concurrentModification(Basket snapshot, RuntimeException cause) {
        return new ShoppingException(ShoppingException.Error.CONCURRENT_MODIFICATION, cause,
                snapshot.basketId().value());
    }

    /**
     * Whether the schema refused the row because the customer already has an active basket — the one constraint this
     * adapter treats as a lost race. Any other violation on the ride (a missing column, a colliding basket identity) is a
     * real failure and is reported as the failure it is, never as "someone else changed it".
     */
    private static boolean isSecondActiveBasket(Throwable failure) {
        return causesOf(failure).stream()
                .filter(SQLException.class::isInstance)
                .map(SQLException.class::cast)
                .anyMatch(refused -> refused.getMessage() != null
                        && refused.getMessage().contains(ACTIVE_BASKET_INDEX));
    }

    private static List<Throwable> causesOf(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    private BasketEntity stored(Basket snapshot) {
        return baskets.findById(snapshot.basketId().value())
                .orElseGet(() -> new BasketEntity(snapshot))
                .setSnapshot(snapshot);
    }

    interface BasketRows extends JpaRepository<BasketEntity, String> {

        Optional<BasketEntity> findByCustomerIdAndActive(String customerId, boolean active);
    }

    @Entity
    @Table(name = "basket")
    static class BasketEntity {

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
        private Basket snapshot;

        protected BasketEntity() {
        }

        BasketEntity(Basket snapshot) {
            this.basketId = snapshot.basketId().value();
            setSnapshot(snapshot);
        }

        Basket snapshot() {
            return snapshot;
        }

        BasketEntity setSnapshot(Basket snapshot) {
            this.snapshot = snapshot;
            this.customerId = snapshot.customerId().value();
            this.active = snapshot.isActive();
            return this;
        }
    }
}
