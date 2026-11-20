package shop.fulfillment.processing;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderLine;

/**
 * The handovers element 12 delivers in the tests: the complete one and the one that is refused for what it does not carry.
 */
public class OrderHandoverFixture {

    private OrderHandoverFixture() {
    }

    public static OrderHandover aHandover() {
        return new OrderHandover(OrderFixture.ORDER_HASH, OrderFixture.PLACED_AT, List.of(OrderFixture.aLine()),
                OrderFixture.aDelivery(), OrderFixture.aPayment(), OrderFixture.anInvoicing());
    }

    public static OrderHandover aHandover(String orderHash) {
        return new OrderHandover(orderHash, OrderFixture.PLACED_AT, List.of(OrderFixture.aLine()),
                OrderFixture.aDelivery(), OrderFixture.aPayment(), OrderFixture.anInvoicing());
    }

    public static OrderHandover aTwoLineHandover() {
        return aTwoLineHandover(OrderFixture.ORDER_HASH);
    }

    public static OrderHandover aTwoLineHandover(String orderHash) {
        List<OrderLine> lines = List.of(OrderFixture.aLine(),
                OrderFixture.aLine("99881", "LEGO Orchidea 99881", 3));
        return new OrderHandover(orderHash, OrderFixture.PLACED_AT, lines,
                OrderFixture.aDelivery(), OrderFixture.aPayment(), OrderFixture.anInvoicing());
    }

    public static OrderHandover anIncompleteHandover() {
        return new OrderHandover("sha256:bad", Instant.parse("2026-11-19T09:31:12Z"), List.of(), null, null, null);
    }
}
