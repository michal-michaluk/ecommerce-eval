package shop.fulfillment.issues;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderLine;
import shop.fulfillment.kernel.ProcessingState;

/**
 * Element 17's dashboard, built from the order processing events and answering the open warehouse-reality gaps grouped by
 * order.
 *
 * <p>It is a read model and nothing else: the board carries no resolver-side command, no issue-resolved event and no issue
 * lifecycle, and this class has no write method at all. An issue leaves the list when the resume command closes it
 * (register D8) — the dashboard never closes, resolves or dismisses one, because no such fact exists in any source.
 *
 * <p>Two tables, because a hold holds the whole order: one row per issue (what was reported, by whom, when) and one row per
 * order (its state, its assignee and the lines the product name is read from). The order facts are copied from the handover
 * payload — the same shape the customer view keeps — so the read path never touches the write model
 * ({@code projection.copy-of-foreign-payload}, {@code projection.no-cross-context-read}).
 *
 * <p>Every handler finds-or-creates its row, records the fact and recomputes the state from the row's own facts, so a repeated
 * delivery rewrites the row it wrote and two orders of arrival end in the same rows. A resume closes <em>every</em> open issue
 * of the order: a resume that leaves one open is refused with {@code 409 ISSUES_REMAIN} (element 09 H6), so a lift that
 * happened is a lift of all of them.
 */
@Component
@Transactional
class IssueDashboardProjection {

    private final IssueRows issues;
    private final OrderRows orders;
    private final Clock clock;

    IssueDashboardProjection(IssueRows issues, OrderRows orders, Clock clock) {
        this.issues = issues;
        this.orders = orders;
        this.clock = clock;
    }

    @EventListener
    public void handle(DomainEvent.OrderProcessingStarted started) {
        orders.save(orderRowOf(started.processingId().value()).startedWith(started.order()));
    }

    @EventListener
    public void handle(DomainEvent.EmployeeAssigned assigned) {
        orders.save(orderRowOf(assigned.processingId().value()).assignedTo(assigned.assignee()));
    }

    @EventListener
    public void handle(DomainEvent.IssueReported reported) {
        issues.save(issueRowOf(reported.issueId().value()).reported(reported));
    }

    @EventListener
    public void handle(DomainEvent.HoldPlaced hold) {
        orders.save(orderRowOf(hold.processingId().value()).held());
    }

    @EventListener
    public void handle(DomainEvent.OrderResumed resumed) {
        orders.save(orderRowOf(resumed.processingId().value()).resumed(resumed.state().wireName()));
        closeEveryIssueOf(resumed.processingId().value());
    }

    /**
     * Every open issue, grouped by the order that holds it and oldest first: the order a row belongs to is readable from the
     * rows beside it, and the group that was reported first comes first (element 17's only ordering default).
     */
    @Transactional(readOnly = true)
    public List<IssueDashboardEntry> findOpen() {
        List<IssueRow> open = issues.findAllByClosedAtIsNullOrderByReportedAtAscIssueIdAsc();
        return byOrder(open, ordersOf(open), clock.instant()).values().stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The lift of a hold closes every issue of the order — a resume with one left open is refused (element 09 H6). */
    private void closeEveryIssueOf(String processingId) {
        Instant closedAt = clock.instant();
        issues.findAllByProcessingIdAndClosedAtIsNull(processingId)
                .forEach(issue -> issues.save(issue.closedAt(closedAt)));
    }

    private Map<String, List<IssueDashboardEntry>> byOrder(List<IssueRow> open, Map<String, OrderRow> ordersById,
                                                          Instant now) {
        Map<String, List<IssueDashboardEntry>> byOrder = new LinkedHashMap<>();
        open.forEach(issue -> byOrder
                .computeIfAbsent(issue.processingId(), processingId -> new ArrayList<>())
                .add(entryOf(issue, ordersById.get(issue.processingId()), now)));
        return byOrder;
    }

    /** The orders the open issues belong to, and only those: the read never walks the whole order table (element 17, R-17-1). */
    private Map<String, OrderRow> ordersOf(List<IssueRow> open) {
        List<String> processingIds = open.stream().map(IssueRow::processingId).distinct().toList();
        return orders.findAllById(processingIds).stream()
                .collect(Collectors.toMap(OrderRow::processingId, order -> order));
    }

    private static IssueDashboardEntry entryOf(IssueRow issue, OrderRow order, Instant now) {
        return IssueDashboardEntry.ofNullable(issue, order, order == null ? null : order.lineName(issue.productNumber()),
                now);
    }

    private IssueRow issueRowOf(String issueId) {
        return issues.findById(issueId).orElseGet(() -> new IssueRow(issueId));
    }

    private OrderRow orderRowOf(String processingId) {
        return orders.findById(processingId).orElseGet(() -> new OrderRow(processingId));
    }

    interface IssueRows extends JpaRepository<IssueRow, String> {

        List<IssueRow> findAllByClosedAtIsNullOrderByReportedAtAscIssueIdAsc();

        List<IssueRow> findAllByProcessingIdAndClosedAtIsNull(String processingId);
    }

    interface OrderRows extends JpaRepository<OrderRow, String> {
    }

    @Entity
    @DynamicUpdate
    @Table(name = "issue_dashboard")
    static class IssueRow {

        @Id
        @Column(name = "issue_id")
        private String issueId;
        @Version
        private long version;
        @Column(name = "processing_id")
        private String processingId;
        @Column(name = "kind")
        private String kind;
        @Column(name = "product_number")
        private String productNumber;
        @Column(name = "reported_by")
        private String reportedBy;
        @Column(name = "reported_at")
        private Instant reportedAt;
        @Column(name = "closed_at")
        private Instant closedAt;

        protected IssueRow() {
        }

        IssueRow(String issueId) {
            this.issueId = issueId;
        }

        /**
         * The fact the report carries. It writes the reported fields only: {@code closedAt} is the resume command's fact and is
         * never unset here, so a re-delivered report cannot resurrect an issue the resolver already closed (element 04 RULE-1).
         */
        IssueRow reported(DomainEvent.IssueReported reported) {
            this.processingId = reported.processingId().value();
            this.kind = reported.kind();
            this.productNumber = reported.productNumber();
            this.reportedBy = reported.reportedBy();
            this.reportedAt = reported.reportedAt();
            return this;
        }

        String issueId() {
            return issueId;
        }

        String processingId() {
            return processingId;
        }

        String kind() {
            return kind;
        }

        String productNumber() {
            return productNumber;
        }

        String reportedBy() {
            return reportedBy;
        }

        Instant reportedAt() {
            return reportedAt;
        }

        /** The one fact that ever moves on a row: the moment the resume command closed it (element 04 RULE-1). */
        IssueRow closedAt(Instant at) {
            this.closedAt = at;
            return this;
        }
    }

    @Entity
    @DynamicUpdate
    @Table(name = "issue_dashboard_order")
    static class OrderRow {

        @Id
        @Column(name = "processing_id")
        private String processingId;
        @Version
        private long version;
        @Column(name = "state")
        private String state;
        @Column(name = "assignee")
        private String assignee;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "lines")
        private List<OrderLine> lines;

        protected OrderRow() {
        }

        OrderRow(String processingId) {
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

        /** The name the order gave the product, or {@code null} when the order does not carry that line. */
        String lineName(String productNumber) {
            if (lines == null) {
                return null;
            }
            return lines.stream()
                    .filter(line -> line.productNumber().equals(productNumber))
                    .map(OrderLine::name)
                    .findFirst()
                    .orElse(null);
        }

        /** The fact only the handover carries: the lines a product name is read from — and nothing else of the order. */
        OrderRow startedWith(Order order) {
            this.lines = List.copyOf(order.lines());
            return this;
        }

        /** The fact a claim carries: who to hand the order back to when the hold is lifted. */
        OrderRow assignedTo(String employeeId) {
            this.assignee = employeeId;
            return this;
        }

        /** The hold is the state the order is in, never a flag beside it (element 09 RULE-2). */
        OrderRow held() {
            this.state = ProcessingState.ON_HOLD.wireName();
            return this;
        }

        /** The state the order was held from, as the resume event reports it (register D12). */
        OrderRow resumed(String resumedState) {
            this.state = resumedState;
            return this;
        }
    }
}
