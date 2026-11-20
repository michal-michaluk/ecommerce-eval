package shop.fulfillment.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingState;

/**
 * Element 18's read model: one row per processing holding the state the customer may see, and one row per delivery leg the
 * lane reported.
 *
 * <p>It is fed by the domain events and by nothing else — the write model of the processing slice is never read here, so the
 * customer's resource cannot accidentally serve a field it does not own ({@code projection.no-cross-context-read}). Every
 * handler does the same thing: find-or-create the row, record the fact, recompute the state from the row's own facts, save.
 * Because the facts are monotonic and the state is derived, the same events delivered in any order end in the same row and a
 * repeated delivery rewrites it ({@code projection.idempotent-recompute}, {@code order-independent}).
 *
 * <p>The state is derived rather than copied for one reason: the events do not carry it. The derivation is total over the
 * aggregate's transitions — pending, assigned, picking, packed, on hold, ready for delivery, delivered — and it never regresses,
 * because no fact it reads can be unset (element 18 RULE-4).
 *
 * <p>The assignee is not a column and the issue is not a column: a hold is a paused state and nothing more, so the customer
 * scope has nothing to reveal even by accident (element 11 RULE-4, D-11-4).
 */
@Component
@Transactional
class CustomerStateProjection {

    private final CustomerStates states;
    private final CustomerLegs legs;
    private final Clock clock;

    CustomerStateProjection(CustomerStates states, CustomerLegs legs, Clock clock) {
        this.states = states;
        this.legs = legs;
        this.clock = clock;
    }

    @EventListener
    public void handle(DomainEvent.OrderProcessingStarted started) {
        CustomerStateRow row = rowOf(started.processingId().value());
        states.save(row.startedWith(started.order()));
    }

    @EventListener
    public void handle(DomainEvent.EmployeeAssigned assigned) {
        CustomerStateRow row = rowOf(assigned.processingId().value());
        states.save(row.assigned());
    }

    @EventListener
    public void handle(DomainEvent.PositionPicked picked) {
        CustomerStateRow row = rowOf(picked.processingId().value());
        states.save(row.picked());
    }

    @EventListener
    public void handle(DomainEvent.PositionPacked packed) {
        CustomerStateRow row = rowOf(packed.processingId().value());
        states.save(row.packed(packed.productNumber()));
    }

    @EventListener
    public void handle(DomainEvent.HoldPlaced hold) {
        CustomerStateRow row = rowOf(hold.processingId().value());
        states.save(row.held());
    }

    @EventListener
    public void handle(DomainEvent.OrderResumed resumed) {
        CustomerStateRow row = rowOf(resumed.processingId().value());
        states.save(row.resumed());
    }

    @EventListener
    public void handle(DomainEvent.ReadyForDeliveryConfirmed confirmed) {
        CustomerStateRow row = rowOf(confirmed.processingId().value());
        states.save(row.readyForDelivery(confirmed.parcelId()));
    }

    @EventListener
    public void handle(DomainEvent.OrderDelivered delivered) {
        CustomerStateRow row = rowOf(delivered.processingId().value());
        states.save(row.delivered());
    }

    /** The first leg is ours: it is recorded when the carrier answered the registration (element 03 RULE-1). */
    @EventListener
    public void handle(DomainEvent.DeliveryNumberGenerated generated) {
        reach(generated.parcelId(), Leg.L1);
    }

    /** The second leg is ours too: the carrier acknowledged our collection request (element 03, L2). */
    @EventListener
    public void handle(DomainEvent.CollectionRequested requested) {
        reach(requested.parcelId(), Leg.L2);
    }

    /** A leg the carrier reported — the only legs we cannot compute (element 03 RULE-4). */
    @EventListener
    public void handle(DomainEvent.DeliveryStateNotified notified) {
        reach(notified.parcelId(), notified.leg());
    }

    /**
     * The customer's view of one processing.
     *
     * @param processingId the processing the customer asks about
     * @return the state, the order summary and the delivery timeline
     */
    @Transactional(readOnly = true)
    public CustomerState findState(String processingId) {
        CustomerStateRow row = states.findById(processingId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
        return Optional.ofNullable(CustomerState.ofNullable(row, stepsOf(row)))
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }

    /** Every leg recorded for the parcel of this row, oldest first; a processing without a parcel has no legs. */
    private List<CustomerState.Step> stepsOf(CustomerStateRow row) {
        if (row.parcelId() == null) {
            return List.of();
        }
        return legs.findAllByParcelIdOrderByLegOrderAsc(row.parcelId()).stream()
                .map(CustomerLegRow::toStep)
                .toList();
    }

    /** Records one leg of one parcel; a leg already recorded keeps the moment we first heard of it. */
    private void reach(ParcelId parcelId, Leg leg) {
        CustomerLegRow reached = new CustomerLegRow(parcelId.value(), leg, Instant.now(clock));
        if (!legs.existsById(reached.legId())) {
            legs.save(reached);
        }
    }

    private CustomerStateRow rowOf(String processingId) {
        return states.findById(processingId).orElseGet(() -> new CustomerStateRow(processingId));
    }

    interface CustomerStates extends JpaRepository<CustomerStateRow, String> {
    }

    interface CustomerLegs extends JpaRepository<CustomerLegRow, String> {

        List<CustomerLegRow> findAllByParcelIdOrderByLegOrderAsc(String parcelId);
    }

    /**
     * One processing as the customer may read it: the order he placed, the state it reached, the parcel carrying it and the
     * facts that state is derived from. No employee, no issue, no invoicing party and no amount is a column of this row.
     */
    @Entity
    @DynamicUpdate
    @Table(name = "customer_processing_state")
    static class CustomerStateRow {

        @Id
        @Column(name = "processing_id")
        private String processingId;
        @Version
        private long version;
        @Column(name = "state")
        private String state;
        @Column(name = "parcel_id")
        private String parcelId;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "order_document")
        private Order order;
        @JdbcTypeCode(SqlTypes.JSON)
        private List<String> packedProducts = new ArrayList<>();
        @Column(name = "picked")
        private boolean picked;
        @Column(name = "assigned")
        private boolean assigned;
        @Column(name = "held")
        private boolean held;
        @Column(name = "ready_for_delivery")
        private boolean readyForDelivery;
        @Column(name = "delivered")
        private boolean delivered;

        protected CustomerStateRow() {
        }

        CustomerStateRow(String processingId) {
            this.processingId = processingId;
        }

        String state() {
            return state;
        }

        String parcelId() {
            return parcelId;
        }

        Order order() {
            return order;
        }

        /** The handover: the order itself, whose lines the summary shows. */
        CustomerStateRow startedWith(Order order) {
            this.order = order;
            return refreshed();
        }

        CustomerStateRow assigned() {
            this.assigned = true;
            return refreshed();
        }

        CustomerStateRow picked() {
            this.picked = true;
            return refreshed();
        }

        /** One position went into the box; a product that is already counted is the same fact, not a second one. */
        CustomerStateRow packed(String productNumber) {
            if (!packedProducts.contains(productNumber)) {
                packedProducts.add(productNumber);
            }
            return refreshed();
        }

        CustomerStateRow held() {
            this.held = true;
            return refreshed();
        }

        CustomerStateRow resumed() {
            this.held = false;
            return refreshed();
        }

        CustomerStateRow readyForDelivery(ParcelId parcelId) {
            this.readyForDelivery = true;
            this.parcelId = parcelId.value();
            return refreshed();
        }

        CustomerStateRow delivered() {
            this.delivered = true;
            return refreshed();
        }

        /** The state, recomputed from the row's own monotonic facts so that any arrival order ends in the same answer. */
        private CustomerStateRow refreshed() {
            this.state = derivedState().name();
            return this;
        }

        private ProcessingState derivedState() {
            if (delivered) {
                return ProcessingState.DELIVERED;
            }
            return reachedState();
        }

        private ProcessingState reachedState() {
            if (readyForDelivery) {
                return ProcessingState.READY_FOR_DELIVERY;
            }
            return held ? ProcessingState.ON_HOLD : unpackedState();
        }

        private ProcessingState unpackedState() {
            if (allPositionsPacked()) {
                return ProcessingState.PACKED;
            }
            if (picked) {
                return ProcessingState.PICKING;
            }
            return assigned ? ProcessingState.ASSIGNED : ProcessingState.PENDING;
        }

        /** Packed once every line of the order is in the box; an order whose handover is still missing is packed by nobody. */
        private boolean allPositionsPacked() {
            return order != null && !order.lines().isEmpty() && packedProducts.size() == order.lines().size();
        }
    }

    /** One leg of one parcel's delivery, keyed by the pair so a repeated notification is the same row. */
    @Entity
    @Table(name = "customer_delivery_leg")
    static class CustomerLegRow {

        @Id
        @Column(name = "leg_id")
        private String legId;
        @Column(name = "parcel_id")
        private String parcelId;
        @Column(name = "leg")
        private String leg;
        @Column(name = "leg_order")
        private int legOrder;
        @Column(name = "reached_at")
        private Instant reachedAt;

        protected CustomerLegRow() {
        }

        CustomerLegRow(String parcelId, Leg leg, Instant reachedAt) {
            this.legId = parcelId + "|" + leg.name();
            this.parcelId = parcelId;
            this.leg = leg.name();
            this.legOrder = leg.ordinal();
            this.reachedAt = reachedAt;
        }

        String legId() {
            return legId;
        }

        CustomerState.Step toStep() {
            return new CustomerState.Step(leg, reachedAt);
        }
    }
}
