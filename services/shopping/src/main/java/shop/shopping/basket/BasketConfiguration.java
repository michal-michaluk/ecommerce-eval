package shop.shopping.basket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The basket's own configuration, in one place and one style for the {@code basket} prefix.
 *
 * <p>The currency an empty basket is opened in. Nothing in the specification says what a basket with no item is priced in,
 * while the API reports a currency on exactly that basket (element 07, {@code basket} step 2), so the deployment states it —
 * {@code SHOPPING_BASKET_CURRENCY} in the cluster, {@code basket.currency} in a profile — and {@link BasketCurrency} refuses a
 * code ISO-4217 does not know, so a misconfigured deployment fails while its bean is created instead of on the first basket.
 *
 * <p>The delivery estimate's block, {@code basket.estimate.*}, binds into the same {@code basket} prefix: one prefix, one
 * properties record, one place a reader looks (element 05 §Configuration).
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
     * @param estimate the {@code basket.estimate.*} block {@link BasketDeliveryEstimate} reads, absent when the deployment
     *                 states none
     */
    @ConfigurationProperties(prefix = "basket")
    record Settings(String currency, EstimateSettings estimate) {

        /**
         * {@code basket.estimate.*} — the three keys element 05 fixes (D36, D43), bound exactly where {@code basket.currency}
         * binds.
         *
         * <p>No {@code @DefaultValue} sits on any component, and that is deliberate: a default filled in here would make an
         * absent key look like a stated one, while element 05's I5 requires an absent key to answer the fallback instead. The
         * block is data — {@link BasketDeliveryEstimate} is the only reader, it decides what an absent or unusable value
         * means, and it answers {@link Estimate#unknown} rather than an exception.
         *
         * @param defaultDays the range in calendar days from the customer's today (D36)
         * @param zone        the zone whose today the range counts from (D43)
         * @param fallback    what to answer when the configuration cannot produce a range; {@code unknown} is its only
         *                    specified value (D7)
         */
        record EstimateSettings(DefaultDays defaultDays, String zone, String fallback) {

            /**
             * {@code basket.estimate.defaultDays} — the two ends of the estimate, in calendar days from today.
             *
             * @param from days from today to the first day of the range, inclusive
             * @param to   days from today to the last day of the range, inclusive
             */
            record DefaultDays(int from, int to) {
            }
        }
    }
}
