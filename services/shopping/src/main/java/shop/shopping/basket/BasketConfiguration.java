package shop.shopping.basket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

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
 *
 * <p>The two pure policies are beans here and nowhere else: element 04's calculation and element 05's estimate read
 * configuration and a caller's instant and hold no state, so this class is the only place that can hand them what they read —
 * and without the beans neither a render nor the handover could price or date anything.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BasketConfiguration.Settings.class)
class BasketConfiguration {

    @Bean
    BasketCurrency basketCurrency(Settings settings) {
        return new BasketCurrency(settings.currency());
    }

    /**
     * Element 08's port as this increment has it (D38): the fixture adapter stands in for a provider that publishes no
     * shop-facing offer read yet, and the browsing slice's adapter replaces it without this wiring changing.
     *
     * <p>It is a bean because the render route is the port's first caller (D60): without it neither the pull on read nor an add
     * could answer anything, and the service would not start with a dependency nobody provides.
     *
     * @param clock the clock the adapter stamps its reads with
     * @return the catalog the basket reads its offers through
     */
    @Bean
    OfferCatalog offerCatalog(Clock clock) {
        return new FixtureOfferCatalog(clock);
    }

    /**
     * Element 04's calculation, charging the delivery the deployment states.
     *
     * <p>An absent {@code basket.delivery} block, or one that states only half of it, answers the two values the capture fixes
     * ({@code 9,99 zł} a shipment, free on SMART) rather than {@code 0,00} for everything: a rate nobody stated is a stated
     * default of the specification, and a silent zero would give delivery away for free.
     *
     * @param settings the {@code basket.*} block
     * @return the calculation the view and the handover price with
     */
    @Bean
    BasketPriceCalculation basketPriceCalculation(Settings settings) {
        return new BasketPriceCalculation(deliveryRatesOf(settings));
    }

    /**
     * Element 05's estimate, reading {@code basket.estimate.*}.
     *
     * <p>The block is passed as it binds, {@code null} included: element 05's I5 requires an absent key to answer
     * {@code unknown}, so no default is applied on the way in (D58).
     *
     * @param settings the {@code basket.*} block
     * @return the policy the handover dates the delivery with
     */
    @Bean
    BasketDeliveryEstimate basketDeliveryEstimate(Settings settings) {
        return new BasketDeliveryEstimate(settings.estimate());
    }

    /**
     * The rate the deployment states, key by key, each absent one answered by the stated default of element 04.
     *
     * <p>Both components are boxed so that "the deployment stated no rate" is observable here instead of arriving as {@code 0}:
     * a block that names only {@code free-when-smart: false} would otherwise price every shipment at {@code 0,00}, and one that
     * names only a rate would silently free a SMART delivery it never meant to free. The default is applied per key, never as a
     * pair, precisely so that a half-stated block cannot flip the other half.
     *
     * @param settings the {@code basket.*} block
     * @return what one shipment costs on the given channel
     */
    static DeliveryRates deliveryRatesOf(Settings settings) {
        Settings.DeliverySettings delivery = settings.delivery();
        DeliveryRates stated = DeliveryRates.defaultRates();
        if (delivery == null) {
            return stated;
        }
        return new DeliveryRates(
                delivery.rate() == null ? stated.ratePerShipment() : delivery.rate(),
                delivery.freeWhenSmart() == null ? stated.freeWhenSmart() : delivery.freeWhenSmart());
    }

    /**
     * The {@code basket.*} block. It stays a plain record: the model carries no framework annotation, so the binding lives
     * on this wiring class and the values arrive already typed.
     *
     * @param currency the ISO-4217 code a new basket is opened in
     * @param estimate the {@code basket.estimate.*} block {@link BasketDeliveryEstimate} reads, absent when the deployment
     *                 states none
     * @param delivery the {@code basket.delivery.*} block {@link BasketPriceCalculation} charges, absent when the deployment
     *                 states none
     */
    @ConfigurationProperties(prefix = "basket")
    record Settings(String currency, EstimateSettings estimate, DeliverySettings delivery) {

        /**
         * {@code basket.delivery.*} — what one seller shipment costs and whether the SMART channel frees it (element 04
         * §Parameters, {@code basket.delivery.rate}, {@code basket.delivery.freeWhenSmart}).
         *
         * <p>No {@code @DefaultValue} sits on either component, and both are boxed: an absent key stays <em>absent</em> and is
         * answered by {@link DeliveryRates#defaultRates()} in the wiring, so a half-stated block is a stated default rather than
         * a rate of {@code 0,00}.
         *
         * @param rate          what one seller shipment costs, in fine units of the basket's currency
         * @param freeWhenSmart whether the SMART channel frees delivery
         */
        record DeliverySettings(Long rate, Boolean freeWhenSmart) {
        }

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
