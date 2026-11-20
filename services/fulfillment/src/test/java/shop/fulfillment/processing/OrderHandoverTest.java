package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The handover is a command record and no rule of its own: what it carries becomes the {@link Order} value, and what it does
 * not carry is refused by that value's own invariants.
 */
class OrderHandoverTest {

    @Test
    void turnsTheCommandIntoTheOrderValue() {
        Order order = OrderHandoverFixture.aHandover().toOrder();

        assertThat(order.orderHash()).isEqualTo("sha256:1f3c9d0a");
        assertThat(order.lines()).singleElement()
                .satisfies(line -> assertThat(line.productNumber()).isEqualTo("75423"));
    }

    @Test
    void carriesEveryLineOfAMultiPositionOrder() {
        assertThat(OrderHandoverFixture.aTwoLineHandover().toOrder().lines()).hasSize(2);
    }

    @Test
    void refusesAHandoverThatDoesNotCarryTheWholeOrder() {
        assertThatThrownBy(() -> OrderHandoverFixture.anIncompleteHandover().toOrder())
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.details())
                        .containsExactly(List.of("lines", "delivery", "payment", "invoicing")));
    }
}
