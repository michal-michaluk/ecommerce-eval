package shop.fulfillment.processing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import java.time.Clock;

import shop.fulfillment.kernel.WarehouseStock;

/**
 * Wires the one InvenTree adapter into the port — and only when a deployment configured a warehouse.
 *
 * <p>A deployment with no warehouse answers no {@link WarehouseStock} bean at all, which is the state phase 1 left the port
 * in: the service boots, and the first claim is refused with {@code WAREHOUSE_NOT_WIRED} rather than reserving nothing and
 * pretending it did (element 13 RULE-7, spec D14). The condition reads the token, because the token is what a deployment
 * cannot accidentally have half of; the URL is required with it and {@link InvenTreeWarehouseClient.Settings} refuses a
 * half-configured deployment when the bean is created.
 *
 * <p>No simulator, no CLI and no fixture lives in {@code src/main}: the port has one production adapter and the tests bring
 * their own double (spec D14).
 *
 * <p>The HTTP client is built from the platform's {@code RestClient.Builder} when the deployment has one and from a plain
 * builder otherwise: this module carries no REST-client starter, so the bean is optional and a configured warehouse must not
 * turn its absence into a startup failure (found by {@code ConfiguredWarehouseWiringTest}).
 */
@Configuration(proxyBeanMethods = false)
class InvenTreeWarehouseIntegration {

    @Bean
    @ConditionalOnProperty(prefix = InvenTreeWarehouseClient.Settings.PREFIX, name = "token")
    WarehouseStock warehouseStock(ObjectProvider<RestClient.Builder> http, Environment configuration, Clock clock,
                                 WarehouseReservationRepository reservations) {
        return new InvenTreeWarehouseClient(http.getIfAvailable(RestClient::builder).build(),
                InvenTreeWarehouseClient.Settings.from(configuration), reservations, clock);
    }
}
