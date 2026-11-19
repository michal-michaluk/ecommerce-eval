package shop.fulfillment.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The orders list of element 15, built from the order processing events and answering a row per current order.
 *
 * <p>One {@code @EventListener} per event type, and every handler does the same four things: find-or-create the row, record
 * the fact the event carries, recompute the state from the row's own facts, save. Nothing is patched in place, so the same
 * events delivered in any order end in the same row ({@code projection.idempotent-recompute}, {@code order-independent}) and
 * a repeated delivery rewrites the row it already wrote.
 *
 * <p>The row carries the facts and derives the state from them — the order and its assignee, whether picking started, which
 * products are in the box, whether the carrier was asked to collect, whether the last leg closed the order and whether an
 * issue holds it. Every fact is monotonic, so no arrival order can lower the row, and a repeated {@code PositionPacked} is
 * the same fact as the first one: the products are a set, never a counter that a redelivery could advance. The progress
 * counter is derived when the list is served, the way a clock-derived field is: it is an answer about the row as it stands,
 * not a fact to store (element 15, element 11's {@code n/m packed}).
 */
@Component
@Transactional
@AllArgsConstructor
class OrdersListProjection {

    private final OrdersList rows;

    @EventListener
    public void handle(DomainEvent.OrderProcessingStarted started) {
        OrdersListRow row = rowOf(started.processingId().value());
        rows.save(row.startedWith(started.order()));
    }

    @EventListener
    public void handle(DomainEvent.EmployeeAssigned assigned) {
        OrdersListRow row = rowOf(assigned.processingId().value());
        rows.save(row.assignedTo(assigned.assignee()));
    }

    /** The first confirmation of element 16 puts the order into picking, whatever the scan repeated later. */
    @EventListener
    public void handle(DomainEvent.PositionPicked picked) {
        OrdersListRow row = rowOf(picked.processingId().value());
        rows.save(row.pickingStarted());
    }

    /** One product went into the box; a product already counted is the same fact, never a second position (element 02 RULE-3). */
    @EventListener
    public void handle(DomainEvent.PositionPacked packed) {
        OrdersListRow row = rowOf(packed.processingId().value());
        rows.save(row.packed(packed.productNumber()));
    }

    /** The order is done locally and the carrier was asked to collect it — element 08's one decision with two effects. */
    @EventListener
    public void handle(DomainEvent.ReadyForDeliveryConfirmed confirmed) {
        OrdersListRow row = rowOf(confirmed.processingId().value());
        rows.save(row.readyForDelivery());
    }

    /** The carrier reported the last leg: the order has left the lane and the list says so (element 18). */
    @EventListener
    public void handle(DomainEvent.OrderDelivered delivered) {
        OrdersListRow row = rowOf(delivered.processingId().value());
        rows.save(row.delivered());
    }

    /** The hold is the state the order is in, and it holds the whole order — the list has one marker for it (element 09 RULE-2). */
    @EventListener
    public void handle(DomainEvent.HoldPlaced hold) {
        OrdersListRow row = rowOf(hold.processingId().value());
        rows.save(row.held());
    }

    /** The lift drops the hold and keeps the state the order was held from, as the event reports it (register D12, element 09 H5). */
    @EventListener
    public void handle(DomainEvent.OrderResumed resumed) {
        OrdersListRow row = rowOf(resumed.processingId().value());
        rows.save(row.resumed(resumed.state()));
    }

    /**
     * Every current order, oldest first — the list is worked from the top, and the placement time is the only ordering key
     * element 15 names (Q-15-1).
     */
    @Transactional(readOnly = true)
    public List<OrdersListEntry> findAllCurrent() {
        return rows.findAllByOrderByPlacedAtAscProcessingIdAsc().stream()
                .map(OrdersListEntry::ofNullable)
                .filter(Objects::nonNull)
                .toList();
    }

    private OrdersListRow rowOf(String processingId) {
        return rows.findById(processingId).orElseGet(() -> new OrdersListRow(processingId));
    }

    interface OrdersList extends JpaRepository<OrdersListRow, String> {

        List<OrdersListRow> findAllByOrderByPlacedAtAscProcessingIdAsc();
    }

    @Entity
    @DynamicUpdate
    @Table(name = "orders_list")
    static class OrdersListRow {

        @Id
        @Column(name = "processing_id")
        private String processingId;
        @Version
        private long version;
        @Column(name = "state")
        private String state;
        @Column(name = "restored_state")
        private String restoredState;
        @Column(name = "assignee")
        private String assignee;
        @Column(name = "recipient")
        private String recipient;
        @Column(name = "placed_at")
        private Instant placedAt;
        @Column(name = "positions_total")
        private int positionsTotal;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "packed_products")
        private List<String> packedProducts = new ArrayList<>();
        @Column(name = "picked")
        private boolean picked;
        @Column(name = "ready_for_delivery")
        private boolean readyForDelivery;
        @Column(name = "delivered")
        private boolean delivered;
        @Column(name = "held")
        private boolean held;

        protected OrdersListRow() {
        }

        OrdersListRow(String processingId) {
            this.processingId = processingId;
        }

        String processingId() {
            return processingId;
        }

        String state() {
            return state;
        }

        String assignee() {
            return assignee;
        }

        String recipient() {
            return recipient;
        }

        Instant placedAt() {
            return placedAt;
        }

        int positionsTotal() {
            return positionsTotal;
        }

        int positionsPacked() {
            return packedProducts.size();
        }

        /** The fact only the handover carries: the order, who receives it, when it was placed and how many positions it has. */
        OrdersListRow startedWith(Order order) {
            this.recipient = order.invoicing().buyerName();
            this.placedAt = order.placedAt();
            this.positionsTotal = order.lines().size();
            return refreshed();
        }

        /** The fact a claim carries; the row keeps whoever holds the order and nothing else changes. */
        OrdersListRow assignedTo(String employeeId) {
            this.assignee = employeeId;
            return refreshed();
        }

        /** The fact a scan carries: picking started, however many positions are still open. */
        OrdersListRow pickingStarted() {
            this.picked = true;
            return refreshed();
        }

        /** One product in the box; a product already counted is the same fact and must not advance the order to packed. */
        OrdersListRow packed(String productNumber) {
            if (!packedProducts.contains(productNumber)) {
                packedProducts.add(productNumber);
            }
            return refreshed();
        }

        /** The fact a readiness carries: the carrier was asked to collect the parcel (element 08). */
        OrdersListRow readyForDelivery() {
            this.readyForDelivery = true;
            return refreshed();
        }

        /** The fact the customer's own step carries: the order is out of the lane (element 18). */
        OrdersListRow delivered() {
            this.delivered = true;
            return refreshed();
        }

        /** The fact an issue carries on the whole order: one hold, however many issues name it (element 09 H1/H2). */
        OrdersListRow held() {
            this.held = true;
            return refreshed();
        }

        /** The fact a resume carries: the hold is gone and the state it was held from is the one the order is in again. */
        OrdersListRow resumed(ProcessingState restored) {
            this.held = false;
            this.restoredState = restored.name();
            return refreshed();
        }

        /**
         * The whole state, recomputed from the row's own facts: on hold while an issue holds it, and until then the furthest of
         * the state the row's own facts reach and the state a lift restored.
         */
        private OrdersListRow refreshed() {
            this.state = derivedState().name();
            return this;
        }

        private ProcessingState derivedState() {
            if (held) {
                return ProcessingState.ON_HOLD;
            }
            return reachedState();
        }

        /** Delivered once the last leg closed the order, ready for delivery once the carrier was asked to collect it. */
        private ProcessingState reachedState() {
            if (delivered) {
                return ProcessingState.DELIVERED;
            }
            return readyForDelivery
                    ? ProcessingState.READY_FOR_DELIVERY
                    : furthest(packedState(), restoredState);
        }

        /** Packed once every position is in the box, picking once one is confirmed, assigned once somebody holds it. */
        private ProcessingState packedState() {
            return allPositionsPacked() ? ProcessingState.PACKED : unpackedState();
        }

        private ProcessingState unpackedState() {
            if (picked) {
                return ProcessingState.PICKING;
            }
            return assignee == null ? ProcessingState.PENDING : ProcessingState.ASSIGNED;
        }

        /** Packed by nobody while the handover has not arrived: the total is what an order is packed against. */
        private boolean allPositionsPacked() {
            return positionsTotal > 0 && positionsPacked() == positionsTotal;
        }

        /**
         * The state a lift restored is a floor, not an answer: the row keeps moving with every later fact, and it lands on
         * the restored state only while its own facts have not reached that far yet (register D12, element 09 H5). A resume
         * never restores a hold of its own — element 02 has no nested hold — so both values sit on the same progression.
         */
        private static ProcessingState furthest(ProcessingState reached, String restored) {
            if (restored == null) {
                return reached;
            }
            ProcessingState restoredState = ProcessingState.valueOf(restored);
            return reached.ordinal() >= restoredState.ordinal() ? reached : restoredState;
        }
    }
}
