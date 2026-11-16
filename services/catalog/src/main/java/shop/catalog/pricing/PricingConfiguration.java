package shop.catalog.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shop.catalog.pricing.ActivePriceCalculation.DiscountPolicy;

/**
 * Binds {@code catalog.pricing.*} into the value objects the model takes, so {@link ActivePriceCalculation} stays
 * framework-free (10): the two documented variants become a {@link DiscountPolicy}, D3 when the keys are absent.
 *
 * <p>{@code catalog.pricing.activation-interval} is read by the poll itself ({@code @Scheduled(fixedDelayString)}) —
 * a bean is not available to an annotation.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PricingConfiguration.PricingSettings.class)
class PricingConfiguration {

    @Bean
    ActivePriceCalculation activePriceCalculation(PricingSettings settings) {
        return ActivePriceCalculation.withPolicy(settings.toDiscountPolicy());
    }

    /** The variants of 10 as configuration: which discount family goes first and whether discounts stack. */
    @ConfigurationProperties(prefix = "catalog.pricing")
    record PricingSettings(DiscountPolicy.Order order, DiscountPolicy.Stacking stacking) {

        PricingSettings {
            order = order == null ? DiscountPolicy.Order.FIXED_FIRST : order;
            stacking = stacking == null ? DiscountPolicy.Stacking.STACKED : stacking;
        }

        DiscountPolicy toDiscountPolicy() {
            return DiscountPolicy.of(order, stacking);
        }
    }
}
