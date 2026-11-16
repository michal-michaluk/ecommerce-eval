package shop.catalog.pricing;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

public class PricingAssert {

    private PricingAssert() {
    }

    public static void assertRefused(PricingException.Error expected, ThrowingCallable call) {
        Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(PricingException.class,
                        refused -> Assertions.assertThat(refused.error()).isEqualTo(expected));
    }
}
