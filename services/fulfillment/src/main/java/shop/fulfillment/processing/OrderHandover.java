package shop.fulfillment.processing;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.Delivery;
import shop.fulfillment.kernel.Invoicing;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderLine;
import shop.fulfillment.kernel.Payment;

/**
 * The handover of element 12 as the client writes it: the finished order and nothing else.
 *
 * <p>It is the command record of {@code POST /orders} and it validates nothing, because the order content is validated by
 * {@link Order} itself (element 01 RULE-3): this record only holds what arrived, so an order refused for a missing component
 * is refused by the domain and answered as {@code 422 ORDER_INCOMPLETE} with the components it named.
 *
 * <p>No money, no seller party and no order number appear here — checkout keeps all three (spec D5, F4).
 */
record OrderHandover(
        String orderHash,
        Instant placedAt,
        List<OrderLine> lines,
        Delivery delivery,
        Payment payment,
        Invoicing invoicing) {

    /**
     * The command as the domain value it carries.
     *
     * @return the finished order, with its invariants enforced by its own constructor
     */
    Order toOrder() {
        return Order.builder()
                .orderHash(orderHash)
                .placedAt(placedAt)
                .lines(lines)
                .delivery(delivery)
                .payment(payment)
                .invoicing(invoicing)
                .build();
    }
}
