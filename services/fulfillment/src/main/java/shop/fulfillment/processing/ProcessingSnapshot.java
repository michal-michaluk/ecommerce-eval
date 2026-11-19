package shop.fulfillment.processing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentIssue;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.ParcelState;

/**
 * The processing resource of element 11: the state read, and the answer every command of the resource returns.
 *
 * <p>One payload, one shape, so any of them re-renders a screen. Three fields are optional and each has exactly one
 * spelling — {@code assignee}, {@code parcel} and {@code issue} are explicit nulls, never absent keys: an absent key means
 * "not part of this payload", never "empty" (element 11:41).
 *
 * <p>The {@code orderHash} is deliberately not part of it: a client addresses the processing by its own identity, and the
 * idempotency key of the handover is no business of any screen. No price, amount, currency or total appears here (spec D5).
 *
 * @param processingId the identity the resource is addressed by
 * @param state        the processing state, as the contract spells it
 * @param assignee     the employee holding the order, or an explicit {@code null} while unclaimed
 * @param positions    one entry per order line
 * @param parcel       the parcel carrying the order, or an explicit {@code null} until the first scan registers it
 * @param issue        the issue the order is held for, or an explicit {@code null} while none is open
 * @param openIssues   how many issues of this processing are still open
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Builder
record ProcessingSnapshot(
        String processingId,
        String state,
        String assignee,
        List<Position> positions,
        Parcel parcel,
        Issue issue,
        int openIssues) {

    ProcessingSnapshot {
        positions = List.copyOf(positions);
    }

    /**
     * One position of the order as the screen shows it.
     *
     * @param productNumber the product of the line
     * @param quantity      the quantity the order pinned
     * @param state         {@code awaitingScan}, {@code picked} or {@code packed}
     */
    record Position(String productNumber, int quantity, String state) {

        static Position of(OrderPosition position) {
            return new Position(position.productNumber(), position.quantity(), position.state().wireName());
        }
    }

    /**
     * The parcel of element 03 as the wire flattens it: our reference, the carrier's waybill and the leg it reached. It is
     * an explicit {@code null} until the first scan registers the parcel — an absent key means "not part of this payload",
     * never "no parcel" (element 11:41).
     *
     * @param reference our own parcel reference
     * @param waybill   the carrier's waybill, the only carrier identity
     * @param legState  the furthest delivery leg the carrier reported
     */
    record Parcel(String reference, String waybill, String legState) {

        static Parcel of(ParcelState parcel) {
            return new Parcel(parcel.reference().value(), parcel.carrierIdentity().value(),
                    parcel.legState().wireName());
        }
    }

    /**
     * The issue the order is held for — the open one, or an explicit {@code null}.
     *
     * @param issueId       the issue as it was reported
     * @param kind          the free-form kind the employee named
     * @param productNumber the product the issue concerns, when it names one
     * @param reportedBy    the employee who reported it
     * @param reportedAt    when it was reported
     * @param closedAt      when it was closed, or {@code null} while it is open
     */
    @Builder
    record Issue(String issueId, String kind, String productNumber, String reportedBy, Instant reportedAt,
                 Instant closedAt) {

        static Issue of(FulfillmentIssue issue) {
            return new Issue(issue.issueId().value(), issue.kind(), issue.productNumber(), issue.reportedBy(),
                    issue.reportedAt(), issue.closedAt());
        }
    }

    /**
     * The snapshot of one processing.
     *
     * @param processing the processing as its commands left it
     * @param parcel     the parcel carrying it, or empty while the first scan has not registered one
     * @return the payload of element 11
     */
    static ProcessingSnapshot of(OrderProcessing processing, Optional<ParcelState> parcel) {
        return new ProcessingSnapshot(processing.processingId().value(), processing.state().wireName(),
                processing.assignee(), processing.positions().stream().map(Position::of).toList(),
                parcel.map(Parcel::of).orElse(null), openIssueOf(processing), openIssuesOf(processing));
    }

    private static Issue openIssueOf(OrderProcessing processing) {
        return processing.issues().stream()
                .filter(FulfillmentIssue::isOpen)
                .map(Issue::of)
                .findFirst()
                .orElse(null);
    }

    private static int openIssuesOf(OrderProcessing processing) {
        return (int) processing.issues().stream()
                .filter(FulfillmentIssue::isOpen)
                .count();
    }
}
