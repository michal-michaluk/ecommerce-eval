package shop.fulfillment.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
 * <p>The row carries the facts and derives the state from them — phase 1 knows two facts, the order and its assignee, and
 * the later events of the lane add theirs to the same derivation. The progress counters and the hold marker are derived when
 * the list is served, the way a clock-derived field is: they are answers about the row as it stands, not facts to store.
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
        @Column(name = "assignee")
        private String assignee;
        @Column(name = "recipient")
        private String recipient;
        @Column(name = "placed_at")
        private Instant placedAt;
        @Column(name = "positions_total")
        private int positionsTotal;
        @Column(name = "positions_packed")
        private int positionsPacked;

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
            return positionsPacked;
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

        /**
         * The whole state, recomputed from the row's own facts: packed once every position is in the box, assigned once
         * somebody holds it, pending until then.
         */
        private OrdersListRow refreshed() {
            this.state = derivedState().name();
            return this;
        }

        private ProcessingState derivedState() {
            if (positionsTotal > 0 && positionsPacked == positionsTotal) {
                return ProcessingState.PACKED;
            }
            return assignee == null ? ProcessingState.PENDING : ProcessingState.ASSIGNED;
        }
    }
}
