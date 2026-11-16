package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import shop.catalog.pricing.ActivePriceCalculation.DiscountPolicy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two variant keys of 10 bound into the value object the policy takes: D3 when the keys are absent, and the
 * documented alternatives when an operator sets them — bound through the same prefix and relaxed names the service
 * reads from {@code application.yml}.
 */
class PricingConfigurationTest {

    @Test
    void defaultsToTheStackingOfD3WhenTheKeysAreAbsent() {
        PricingConfiguration.PricingSettings settings = new PricingConfiguration.PricingSettings(null, null);

        assertThat(settings.toDiscountPolicy()).isEqualTo(DiscountPolicy.defaultPolicy());
    }

    @Test
    void bindsTheTwoDocumentedVariants() {
        PricingConfiguration.PricingSettings settings = bound(
                "catalog.pricing.order", "percent-first", "catalog.pricing.stacking", "exclusive");

        DiscountPolicy policy = settings.toDiscountPolicy();

        assertThat(policy.order()).isEqualTo(DiscountPolicy.Order.PERCENT_FIRST);
        assertThat(policy.stacking()).isEqualTo(DiscountPolicy.Stacking.EXCLUSIVE);
    }

    @Test
    void leavesTheDefaultsWhenOnlyOneKeyIsSet() {
        PricingConfiguration.PricingSettings settings = bound("catalog.pricing.stacking", "exclusive");

        assertThat(settings.toDiscountPolicy().order()).isEqualTo(DiscountPolicy.Order.FIXED_FIRST);
        assertThat(settings.toDiscountPolicy().stacking()).isEqualTo(DiscountPolicy.Stacking.EXCLUSIVE);
    }

    private static PricingConfiguration.PricingSettings bound(String... keyValuePairs) {
        MockEnvironment environment = new MockEnvironment();
        for (int index = 0; index < keyValuePairs.length; index += 2) {
            environment.setProperty(keyValuePairs[index], keyValuePairs[index + 1]);
        }
        return Binder.get(environment)
                .bind("catalog.pricing", Bindable.of(PricingConfiguration.PricingSettings.class))
                .orElseThrow(() -> new IllegalStateException("catalog.pricing did not bind"));
    }
}
