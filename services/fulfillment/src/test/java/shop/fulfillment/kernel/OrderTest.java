package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.fulfillment.kernel.OrderFixture.PRODUCT_NAME;

/**
 * Element 01's value object: content only, no identity, equality by the canonical hash, and every missing or contradictory
 * component refused in the constructor rather than downstream.
 */
class OrderTest {

    @Test
    void holdsTheContentTheHandoverCarried() {
        Order order = OrderFixture.anOrder();

        assertThat(order.lines()).containsExactly(OrderFixture.aLine());
        assertThat(order.delivery()).isEqualTo(OrderFixture.aDelivery());
        assertThat(order.invoicing()).isEqualTo(OrderFixture.anInvoicing());
        assertThat(order.orderHash()).isEqualTo(OrderFixture.ORDER_HASH);
    }

    @Test
    void holdsLinesInProductNumberOrderWhateverOrderTheyArrivedIn() {
        assertThat(OrderFixture.orderInListingOrder().lines())
                .extracting(OrderLine::productNumber)
                .containsExactly("75423", "99881");
    }

    @Test
    void isTheSameOrderWhenTheCanonicalHashAgrees() {
        Order first = OrderFixture.anOrder();
        Order second = OrderFixture.anOrder();

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void isNotAnotherOrderWhenTheCanonicalHashDiffers() {
        assertThat(OrderFixture.anOrder()).isNotEqualTo(OrderFixture.anOrder("sha256:other"));
    }

    @Test
    void isNotEqualToAnythingElse() {
        assertThat(OrderFixture.anOrder()).isNotEqualTo("sha256:1f3c9d0a");
    }

    @Test
    void refusesAnOrderWithoutItsCanonicalHash() {
        assertThatThrownBy(() -> OrderFixture.anOrder(" "))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ORDER_INCOMPLETE);
                    assertThat(refused.details()).containsExactly(List.of("orderHash"));
                });
    }

    @Test
    void refusesAnOrderWithoutItsPlacementTime() {
        assertThatThrownBy(() -> order(null, List.of(OrderFixture.aLine()), OrderFixture.aDelivery()))
                .hasMessageContaining("placedAt");
    }

    @Test
    void refusesAnOrderWithoutLines() {
        assertThatThrownBy(() -> order(OrderFixture.PLACED_AT, List.of(), OrderFixture.aDelivery()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused ->
                        assertThat(refused.details()).containsExactly(List.of("lines")));
    }

    @Test
    void refusesAnOrderWithoutItsDelivery() {
        assertThatThrownBy(() -> order(OrderFixture.PLACED_AT, List.of(OrderFixture.aLine()), null))
                .hasMessageContaining("delivery");
    }

    @Test
    void namesEveryMissingComponentAtOnce() {
        assertThatThrownBy(() -> Order.builder().orderHash(OrderFixture.ORDER_HASH).build())
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.details())
                        .containsExactly(List.of("placedAt", "lines", "delivery", "payment", "invoicing")));
    }

    @Test
    void refusesTwoLinesForOneProduct() {
        List<OrderLine> duplicated = List.of(OrderFixture.aLine(),
                OrderFixture.aLine(OrderFixture.PRODUCT_NUMBER, PRODUCT_NAME, 2));

        assertThatThrownBy(() -> Order.builder()
                .orderHash(OrderFixture.ORDER_HASH)
                .placedAt(OrderFixture.PLACED_AT)
                .lines(duplicated)
                .delivery(OrderFixture.aDelivery())
                .payment(OrderFixture.aPayment())
                .invoicing(OrderFixture.anInvoicing())
                .build())
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ORDER_LINE_DUPLICATE));
    }

    private static Order order(java.time.Instant placedAt, List<OrderLine> lines, Delivery delivery) {
        return Order.builder()
                .orderHash(OrderFixture.ORDER_HASH)
                .placedAt(placedAt)
                .lines(lines)
                .delivery(delivery)
                .payment(OrderFixture.aPayment())
                .invoicing(OrderFixture.anInvoicing())
                .build();
    }
}
