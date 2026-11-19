package shop.fulfillment.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;

/**
 * The {@link OrderProcessingRepository} document-snapshot adapter: one JSONB row per processing holding the aggregate's own
 * snapshot, plus the two columns a query reads without opening the document.
 *
 * <p>{@code @Version} turns a lost update into a failure instead of a silent overwrite, which is what makes the claim of
 * element 07 atomic: two employees racing for one order cannot both write {@code assignee} (RULE-6).
 *
 * <p>The adapter drains the aggregate's event buffer before writing, so a failed write cannot publish a fact twice, and
 * publishes every event once after the row is written ({@code persistence.drain-events},
 * {@code persistence.publish-after-write}). Publication happens inside the caller's transaction: the one listener — the orders
 * projection — joins it, so a claim that rolls back leaves no row behind and a claim that commits leaves exactly one.
 *
 * <p>The aggregate snapshot itself is not published: it is package-private, has no subscriber in this phase and no consumer
 * outside the slice, and the list of element 15 is fed by the events (recorded in {@code docs/phase-1-decisions.md}).
 */
@Primary
@Repository
class DocumentOrderProcessingRepository implements OrderProcessingRepository {

    private static final String MINTED_ID = "OP-%d-%04d";
    private static final String MINTED_ISSUE_ID = "ISS-%d-%04d";

    private final Processings processings;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    DocumentOrderProcessingRepository(Processings processings, ApplicationEventPublisher publisher, Clock clock) {
        this.processings = processings;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public Optional<OrderFulfillmentProcess> get(String processingId) {
        return processings.findById(processingId).map(DocumentOrderProcessingRepository::rehydrate);
    }

    @Override
    public Optional<OrderFulfillmentProcess> findByOrderHash(String orderHash) {
        return processings.findByOrderHash(orderHash).map(DocumentOrderProcessingRepository::rehydrate);
    }

    @Override
    public Optional<OrderFulfillmentProcess> findByParcelId(ParcelId parcelId) {
        return processings.findByParcelId(parcelId.value()).map(DocumentOrderProcessingRepository::rehydrate);
    }

    @Override
    public OrderProcessingId nextIdentity() {
        long number = Optional.ofNullable(processings.nextIdentity()).orElse(1L);
        return new OrderProcessingId(MINTED_ID.formatted(Year.now(clock).getValue(), number));
    }

    @Override
    public IssueId nextIssueIdentity() {
        long number = Optional.ofNullable(processings.nextIssueIdentity()).orElse(1L);
        return new IssueId(MINTED_ISSUE_ID.formatted(Year.now(clock).getValue(), number));
    }

    @Override
    public void save(OrderFulfillmentProcess processing) {
        List<DomainEvent> emitted = eventsEmittedFrom(processing);
        processings.save(stored(processing.toSnapshot()));
        emitted.forEach(event -> publisher.publishEvent(event));
    }

    /** Copies the aggregate's buffer and clears it before the write, so a failed save cannot publish a fact twice. */
    static List<DomainEvent> eventsEmittedFrom(OrderFulfillmentProcess processing) {
        List<DomainEvent> emitted = List.copyOf(processing.events);
        processing.events.clear();
        return emitted;
    }

    /**
     * The aggregate rebuilt from its snapshot: the constructor is the all-args one on purpose, and every list is rehydrated
     * as a mutable one so a loaded aggregate behaves exactly like a freshly started one.
     */
    private static OrderFulfillmentProcess rehydrate(OrderProcessingEntity entity) {
        OrderProcessing snapshot = entity.snapshot();
        return new OrderFulfillmentProcess(snapshot.processingId(), new ArrayList<>(), snapshot.order(),
                snapshot.assignee(), snapshot.state(), new ArrayList<>(snapshot.positions()), snapshot.parcelId(),
                snapshot.heldState(), new ArrayList<>(snapshot.issues()));
    }

    private OrderProcessingEntity stored(OrderProcessing snapshot) {
        return processings.findById(snapshot.processingId().value())
                .orElseGet(() -> new OrderProcessingEntity(snapshot.processingId().value()))
                .setSnapshot(snapshot);
    }

    interface Processings extends JpaRepository<OrderProcessingEntity, String> {

        Optional<OrderProcessingEntity> findByOrderHash(String orderHash);

        Optional<OrderProcessingEntity> findByParcelId(String parcelId);

        @Query(value = "select nextval('order_processing_id_seq')", nativeQuery = true)
        Long nextIdentity();

        @Query(value = "select nextval('issue_id_seq')", nativeQuery = true)
        Long nextIssueIdentity();
    }

    @Entity
    @Table(name = "order_processing")
    static class OrderProcessingEntity {

        @Id
        @Column(name = "processing_id")
        private String processingId;
        @Version
        private long version;
        @Column(name = "order_hash")
        private String orderHash;
        @Column(name = "parcel_id")
        private String parcelId;
        @JdbcTypeCode(SqlTypes.JSON)
        private OrderProcessing snapshot;

        protected OrderProcessingEntity() {
        }

        OrderProcessingEntity(String processingId) {
            this.processingId = processingId;
        }

        OrderProcessing snapshot() {
            return snapshot;
        }

        OrderProcessingEntity setSnapshot(OrderProcessing snapshot) {
            this.snapshot = snapshot;
            this.orderHash = snapshot.orderHash();
            this.parcelId = snapshot.parcelId() == null ? null : snapshot.parcelId().value();
            return this;
        }
    }
}
