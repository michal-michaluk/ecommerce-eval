package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deployment that configured a warehouse: the conditional bean is created, it is the InvenTree adapter and not a second
 * implementation, and the settings it reads are the deployment's own (element 13, phase-2 decision P2-9).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "fulfillment.warehouse.url=http://inventree.test",
        "fulfillment.warehouse.token=t0ken"})
class ConfiguredWarehouseWiringTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void wiresTheInvenTreeAdapterWhenTheDeploymentConfiguredOne() {
        assertThat(context.getBeansOfType(WarehouseStock.class)).hasSize(1);
        assertThat(context.getBean(WarehouseStock.class)).isInstanceOf(InvenTreeWarehouseClient.class);
    }
}
