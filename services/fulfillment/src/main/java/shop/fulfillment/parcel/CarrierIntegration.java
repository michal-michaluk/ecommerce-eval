package shop.fulfillment.parcel;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import shop.fulfillment.kernel.CarrierPort;

/**
 * Wires the one carrier adapter into {@link CarrierPort} — and only when a deployment configured a carrier.
 *
 * <p>A deployment with no carrier answers no {@link CarrierPort} bean at all: the service boots, and the first command that
 * needs the carrier — a scan, a readiness, a first print — is refused with {@code CARRIER_NOT_WIRED} rather than pretending it
 * reached anybody (element 14, spec D14). The condition reads the token, because the token is what a deployment cannot
 * accidentally have half of; the URL is required with it and {@link CarrierHttpClient.Settings} refuses a half-configured
 * deployment when the bean is created.
 *
 * <p>The registration block is configuration too, and it is registered unconditionally: the sender that prints on the label,
 * the weight of one parcel (its source is open in the sources, element 03 RULE-6) and one parcel per processing (register D10).
 * A deployment that configures a carrier without a sender is refused at its first registration instead of at startup, so the
 * service still boots without a carrier.
 *
 * <p>No simulator, no CLI and no fixture lives in {@code src/main}: the port has one production adapter and the tests bring
 * their own double (spec D14). The HTTP client comes from the platform's builder when the deployment has one and from a plain
 * builder otherwise, the same way the warehouse adapter is built.
 */
@Configuration(proxyBeanMethods = false)
class CarrierIntegration {

    static final int DEFAULT_WEIGHT_GRAMS = 1000;

    private static final String SENDER = CarrierHttpClient.Settings.PREFIX + ".sender.";
    private static final String WEIGHT_GRAMS = CarrierHttpClient.Settings.PREFIX + ".weight-grams";

    @Bean
    @ConditionalOnProperty(prefix = CarrierHttpClient.Settings.PREFIX, name = "token")
    CarrierPort carrierPort(ObjectProvider<RestClient.Builder> http, Environment configuration) {
        return new CarrierHttpClient(http.getIfAvailable(RestClient::builder).build(),
                CarrierHttpClient.Settings.from(configuration));
    }

    @Bean
    CarrierRegistration carrierRegistration(Environment configuration) {
        return CarrierRegistration.builder()
                .senderStreet(configuration.getProperty(SENDER + "street", ""))
                .senderCity(configuration.getProperty(SENDER + "city", ""))
                .senderPostalCode(configuration.getProperty(SENDER + "postal-code", ""))
                .senderCountry(configuration.getProperty(SENDER + "country", ""))
                .weightGrams(configuration.getProperty(WEIGHT_GRAMS, Integer.class, DEFAULT_WEIGHT_GRAMS))
                .build();
    }
}
