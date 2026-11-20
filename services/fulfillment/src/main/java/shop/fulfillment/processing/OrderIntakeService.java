package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Order;

/**
 * The order processing context's intake: one handover, one load by the order's own idempotency key, one save.
 *
 * <p>Completeness is {@link Order}'s, exclusivity is {@link AssignmentGuard}'s and the first state is the factory's — this class
 * answers whether the order is already being processed and, if it is not, opens the processing and stores it (element 12
 * RULE-1…RULE-4).
 *
 * <p>The duplicate answer is read through {@code findByOrderHash}, so a second delivery of the same order touches nothing,
 * emits nothing and creates no second position set (RULE-2, register D4). The hash alone cannot answer that, though: it is the
 * client's claim, not a value this context computed, so the delivery is a duplicate only when its content's digest is the one
 * the processing was opened with. The same hash over a different order is a contract error — {@code 409 ORDER_HASH_CONFLICT} —
 * instead of a silent {@code 200} that would swallow an order the lane never took.
 */
@Service
@Transactional
class OrderIntakeService {

    private final OrderProcessingRepository processings;

    OrderIntakeService(OrderProcessingRepository processings) {
        this.processings = processings;
    }

    /**
     * Takes one handed-over order into processing, or answers the processing it already has.
     *
     * @param handover the finished order as checkout delivered it
     * @return the processing and whether this delivery was a duplicate
     */
    OrderAcceptance accept(OrderHandover handover) {
        Order order = handover.toOrder();
        return processings.findByOrderHash(order.orderHash())
                .map(processing -> duplicate(processing, order))
                .orElseGet(() -> new OrderAcceptance(startProcessing(order), false));
    }

    private static OrderAcceptance duplicate(OrderFulfillmentProcess processing, Order order) {
        OrderProcessing started = processing.toSnapshot();
        if (!started.order().canonicalDigest().equals(order.canonicalDigest())) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_HASH_CONFLICT, order.orderHash());
        }
        return new OrderAcceptance(started, true);
    }

    private OrderProcessing startProcessing(Order order) {
        OrderFulfillmentProcess processing = OrderFulfillmentProcess.startProcessing(processings.nextIdentity(), order);
        processings.save(processing);
        return processing.toSnapshot();
    }
}
