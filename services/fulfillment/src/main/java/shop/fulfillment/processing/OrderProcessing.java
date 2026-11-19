package shop.fulfillment.processing;

import lombok.Builder;

import java.util.List;

/**
 * The immutable state of one order processing as the read side serves it: who owns it, how far each position got,
 * whether it is on hold, which parcel carries it and which issues it accounts for.
 *
 * <p>Only {@link OrderProcessingEditor} constructs it; the order content itself is pinned by the aggregate and is not
 * copied here beyond its idempotency key.
 */
@Builder
record OrderProcessing(
        OrderProcessingId processingId,
        String orderHash,
        String assignee,
        ProcessingState state,
        List<OrderPosition> positions,
        ParcelId parcelId,
        List<FulfillmentIssue> issues) {
}
