package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A deployment with no warehouse configured: the service boots, the port stays {@code Optional}-wired because no adapter
 * bean exists at all, and the first claim is refused with {@code WAREHOUSE_NOT_WIRED} instead of claiming an order whose
 * stock nobody reserved (element 13 RULE-7, spec D14, phase-1 decision 2.6).
 */
@IntegrationTest
class WarehouseWiringTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;

    @Test
    void bootsWithoutAWarehouseAdapter() {
        assertThat(context.getBeanNamesForType(WarehouseStock.class)).isEmpty();
    }

    @Test
    void refusesTheFirstClaimInsteadOfClaimingWithoutAReservation() {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();

        assertThatThrownBy(() -> assignments.claim(processingId, "employee-42"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_NOT_WIRED));
    }
}
