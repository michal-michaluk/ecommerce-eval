package shop.fulfillment.processing;

import lombok.Builder;

import java.util.List;

import shop.fulfillment.kernel.FulfillmentIssue;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The immutable state of one order processing as the read side serves it: the order the processing is pinned to, who owns
 * it, how far each position got, whether it is on hold, which parcel carries it and which issues it accounts for.
 *
 * <p>The pinned {@link Order} is part of the snapshot because a persistence adapter rehydrates the aggregate from it and
 * never reads a field of the aggregate directly; the order content itself stays immutable, so carrying it here copies no
 * decision. {@code heldState} is here for the same reason: a restart has to resume a held order to the state it was held
 * from (element 09 H5).
 */
@Builder
record OrderProcessing(
        OrderProcessingId processingId,
        Order order,
        String orderHash,
        String assignee,
        ProcessingState state,
        ProcessingState heldState,
        List<OrderPosition> positions,
        ParcelId parcelId,
        List<FulfillmentIssue> issues) {
}
