package shop.shopping.basket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The basket's own configuration, in one place and one style for the {@code basket} prefix.
 *
 * <p>One setting so far: the currency an empty basket is opened in. Nothing in the specification says what a basket with no
 * item is priced in, while the API reports a currency on exactly that basket (element 07, {@code basket} step 2), so the
 * deployment states it — {@code SHOPPING_BASKET_CURRENCY} in the cluster, {@code basket.currency} in a profile — and
 * {@link BasketCurrency} refuses a code ISO-4217 does not know, so a misconfigured deployment fails while its bean is created
 * instead of on the first basket.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BasketConfiguration.Settings.class)
class BasketConfiguration {

    @Bean
    BasketCurrency basketCurrency(Settings settings) {
        return new BasketCurrency(settings.currency());
    }

    /**
     * The {@code basket.*} block. It stays a plain record: the model carries no framework annotation, so the binding lives
     * on this wiring class and the values arrive already typed.
     *
     * @param currency the ISO-4217 code a new basket is opened in
     */
    @ConfigurationProperties(prefix = "basket")
    record Settings(String currency) {
    }
}
