package shop.fulfillment.processing;

import java.util.Optional;

import shop.fulfillment.kernel.OrderProcessingId;

/**
 * The one secondary port of the order fulfillment process: the adapter rehydrates the aggregate and saves it back,
 * whichever storage shape it uses (persistence-adapters.md).
 *
 * <p>{@link #findByOrderHash} exists because the handover of element 12 is idempotent on the order's own content: a second
 * delivery of the same {@code orderHash} answers the processing that already exists instead of opening a second one
 * (element 12 RULE-2, register D4), and that question cannot be asked of the aggregate's identity, which is minted here.
 *
 * <p>{@link #nextIdentity} mints that identity. It lives behind the port because it is storage, not a decision: a
 * sequence the database owns is what makes two concurrent intakes unable to draw the same {@code processingId}.
 */
interface OrderProcessingRepository {

    Optional<OrderFulfillmentProcess> get(String processingId);

    Optional<OrderFulfillmentProcess> findByOrderHash(String orderHash);

    OrderProcessingId nextIdentity();

    void save(OrderFulfillmentProcess processing);
}
