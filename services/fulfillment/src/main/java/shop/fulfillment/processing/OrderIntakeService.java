package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.Order;

/**
 * The order processing context's intake: one handover, one load by the order's own idempotency key, one save.
 *
 * <p>It holds no rule. Completeness is {@link Order}'s, exclusivity is {@link AssignmentGuard}'s and the first state is the
 * factory's — this class only answers whether the order is already being processed and, if it is not, opens the processing
 * and stores it (element 12 RULE-1…RULE-4).
 *
 * <p>The duplicate answer is read through {@code findByOrderHash}, so a second delivery of the same order touches nothing,
 * emits nothing and creates no second position set (RULE-2, register D4).
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
                .map(processing -> new OrderAcceptance(processing.toSnapshot(), true))
                .orElseGet(() -> new OrderAcceptance(startProcessing(order), false));
    }

    private OrderProcessing startProcessing(Order order) {
        OrderFulfillmentProcess processing = OrderFulfillmentProcess.startProcessing(processings.nextIdentity(), order);
        processings.save(processing);
        return processing.toSnapshot();
    }
}
