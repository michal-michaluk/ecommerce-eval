package shop.fulfillment.processing;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The one write that touches an issue — {@code POST /processing/{processingId}/hold/resume}: the issues the caller vouches
 * for, as the resume command of register D7/D8 defines it.
 *
 * <p>Closing is a fact of this command and of nothing else, so the body names what it closes. An empty list is a legal
 * request: it closes nothing, and the command answers the hold it could not lift (element 09 H6) or the no-op of an order
 * that is not held (H7). An id this order does not account for is refused as the unknown resource it is (register D11).
 */
record ResumeHold(@NotNull List<String> closedIssueIds) {

    ResumeHold {
        closedIssueIds = closedIssueIds == null ? null : List.copyOf(closedIssueIds);
    }
}
