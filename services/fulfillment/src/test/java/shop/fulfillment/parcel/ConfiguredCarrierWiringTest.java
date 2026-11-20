package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.CarrierPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deployment that configured a carrier: the conditional bean is created, it is the HTTP adapter and not a second
 * implementation, and the sender block the registration prints arrives with it (element 14, phase-3 decisions P3-1…P3-9).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "fulfillment.carrier.url=http://carrier.test",
        "fulfillment.carrier.token=t0ken"})
class ConfiguredCarrierWiringTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void wiresTheCarrierAdapterWhenTheDeploymentConfiguredOne() {
        assertThat(context.getBeansOfType(CarrierPort.class)).hasSize(1);
        assertThat(context.getBean(CarrierPort.class)).isInstanceOf(CarrierHttpClient.class);
    }

    @Test
    void wiresTheRegistrationBlockTheAdapterPrintsFrom() {
        assertThat(context.getBean(CarrierRegistration.class).weightGrams()).isPositive();
    }
}
