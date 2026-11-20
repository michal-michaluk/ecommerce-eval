package shop.fulfillment.issues;

import java.time.Duration;
import java.time.Instant;

/**
 * One row of element 17's dashboard: an issue as the resolver reads it, together with the order it holds.
 *
 * <p>The row is flat and repeats the order's own facts on purpose — the contract asserts {@code $.items[0].issueId} with no
 * group wrapper, so grouping by order is the ordering of the list, and every row says which order it belongs to and what that
 * order is doing. An issue holds the whole order (element 09 RULE-1), so two rows of one order never disagree about the state.
 *
 * <p>What no row carries is as decided as what it carries: no quantity (element 04 Q-04-5 — a partial close has no
 * expression), no location (Q-17-2 — the pick list owns locations), no money (spec D5) and no action (element 17's action table
 * is empty by finding).
 *
 * @param processingId the processing whose order is held
 * @param orderState   the state that order is in — {@code onHold} while the row is listed
 * @param assignee     the employee who holds the order, or {@code null} while nobody does
 * @param issueId      the issue, and the key the resume command names (register D8)
 * @param kind         the free-form kind, verbatim — never enumerated and never rejected
 * @param product      the piece the resolver has to look for
 * @param reportedBy   the employee who reported it
 * @param reportedAt   when it was reported
 * @param ageSeconds   how long ago that was, read at the moment the list is served — never negative, because the reporter's clock
 *                     and this service's may disagree
 */
record IssueDashboardEntry(
        String processingId,
        String orderState,
        String assignee,
        String issueId,
        String kind,
        Product product,
        String reportedBy,
        Instant reportedAt,
        long ageSeconds) {

    /**
     * The product as the resolver needs it: the number the warehouse knows and the name the order gave it.
     *
     * @param productNumber the product of the issue's position
     * @param name          the line name from the order the processing carries
     */
    record Product(String productNumber, String name) {
    }

    /**
     * One issue as one row, or {@code null} when the order it belongs to never arrived — a claim or an issue whose handover
     * has not been projected has no state to show, and the list never shows a half row
     * ({@code projection.absent-read-model-is-null}).
     *
     * @param issue      the issue row
     * @param order      the order row the issue belongs to, or {@code null} when it is not projected yet
     * @param productName the line name of the issue's product, or {@code null} when the order does not carry it
     * @param now        the moment the list is served, for the age of the row
     * @return the entry, or {@code null}
     */
    static IssueDashboardEntry ofNullable(IssueDashboardProjection.IssueRow issue,
                                          IssueDashboardProjection.OrderRow order,
                                          String productName, Instant now) {
        if (order == null || order.state() == null) {
            return null;
        }
        return new IssueDashboardEntry(order.processingId(), order.state(), order.assignee(),
                issue.issueId(), issue.kind(), new Product(issue.productNumber(), productName),
                issue.reportedBy(), issue.reportedAt(),
                Math.max(0, Duration.between(issue.reportedAt(), now).toSeconds()));
    }
}
