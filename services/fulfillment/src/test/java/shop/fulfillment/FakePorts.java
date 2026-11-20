package shop.fulfillment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.WarehouseStock;
import shop.fulfillment.parcel.FakeCarrierPort;
import shop.fulfillment.processing.FakeWarehouseStock;

/**
 * The two ports of this context as the test world answers them (spec D14: the port plus an in-test fake, no HTTP), in one place.
 *
 * <p>A test that needs the whole lane — hand over, claim, scan, readiness — imports this configuration instead of declaring the
 * same two beans a twelfth time; a test that asserts a port is <em>absent</em> (`WarehouseWiringTest`) does not import it, which is
 * why it is not part of {@link IntegrationConfiguration}.
 */
@TestConfiguration
public class FakePorts {

    @Bean
    WarehouseStock warehouseStock() {
        return new FakeWarehouseStock();
    }

    @Bean
    CarrierPort carrierPort() {
        return new FakeCarrierPort();
    }
}
