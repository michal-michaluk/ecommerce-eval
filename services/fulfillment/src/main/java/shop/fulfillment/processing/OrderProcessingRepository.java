package shop.fulfillment.processing;

import java.util.Optional;

import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;

/**
 * The one secondary port of the order fulfillment process: the adapter rehydrates the aggregate and saves it back,
 * whichever storage shape it uses (persistence-adapters.md).
 *
 * <p>{@link #findByOrderHash} exists because the handover of element 12 is idempotent on the order's own content: a second
 * delivery of the same {@code orderHash} answers the processing that already exists instead of opening a second one
 * (element 12 RULE-2, register D4), and that question cannot be asked of the aggregate's identity, which is minted here.
 *
 * <p>{@link #findByParcelId} exists for the other direction of the same problem: the carrier reports a leg against the parcel's
 * identity, and the order that consumes the last leg is addressed by its processing id. One parcel carries one processing
 * (register D10), so the parcel id names at most one aggregate.
 *
 * <p>{@link #nextIdentity} mints that identity. It lives behind the port because it is storage, not a decision: a
 * sequence the database owns is what makes two concurrent intakes unable to draw the same {@code processingId}.
 */
interface OrderProcessingRepository {

    Optional<OrderFulfillmentProcess> get(String processingId);

    Optional<OrderFulfillmentProcess> findByOrderHash(String orderHash);

    Optional<OrderFulfillmentProcess> findByParcelId(ParcelId parcelId);

    OrderProcessingId nextIdentity();

    /**
     * Mints the identity of one reported issue — {@code ISS-YYYY-NNNN}, from the sequence the database owns, for the same
     * reason {@link #nextIdentity} lives here: two employees reporting at once must not draw the same id, and an id minted in
     * a service counter is one every instance shares.
     *
     * @return the identity the issue record is written under
     */
    IssueId nextIssueIdentity();

    void save(OrderFulfillmentProcess processing);
}
