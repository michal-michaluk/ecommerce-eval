package shop.fulfillment.kernel;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One report of a warehouse reality gap: what the warehouse system claims and what is physically on the shelf
 * disagree (element 04).
 *
 * <p>The record is written once and never mutated, with one exception — {@code closedAt}, the only fact that ever
 * moves on it, and it moves only through the resume command (register D8). It carries no state of its own: the hold it
 * causes is {@link ProcessingState#ON_HOLD} on the order, decided by the hold decision.
 *
 * <p>{@code kind} is a free-form code; {@link IssueKinds} holds the four the transcript names and never closes the
 * set. No amount, price or quantity appears here.
 */
@Builder(toBuilder = true)
public record FulfillmentIssue(
        IssueId issueId,
        OrderProcessingId processingId,
        String kind,
        String productNumber,
        String reportedBy,
        Instant reportedAt,
        Instant closedAt) {

    public FulfillmentIssue {
        Objects.requireNonNull(issueId, "issueId");
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(reportedAt, "reportedAt");
        ensurePresent(kind, "kind");
        ensurePresent(productNumber, "productNumber");
        ensurePresent(reportedBy, "reportedBy");
    }

    /** An issue is open until the resume command names it; there is no other issue lifecycle. */
    public boolean isOpen() {
        return closedAt == null;
    }

    /**
     * The same issue, closed by the resume command.
     *
     * @param at the moment the close is recorded
     * @return a new record; the open one stays as it was reported
     */
    public FulfillmentIssue close(Instant at) {
        return toBuilder().closedAt(at).build();
    }

    private static void ensurePresent(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ISSUE_INCOMPLETE, List.of(name));
        }
    }
}
