package shop.catalog.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.pricing.PricingAssert.assertRefused;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.MAY_1;
import static shop.catalog.pricing.PricingFixture.PLN;
import static shop.catalog.pricing.PricingFixture.openFrom;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.until;

class PricingValuesTest {

    @Test
    void moneyTakesItsScaleFromTheCurrency() {
        assertThat(pln(30989).scale()).isEqualTo(2);
        assertThat(PricingFixture.jpy(30989).scale()).isEqualTo(0);
    }

    @Test
    void anUnknownCurrencyCodeIsRefused() {
        assertRefused(PricingException.Error.CURRENCY_UNKNOWN, () -> new Money(1, "XYZ"));
    }

    @Test
    void aNegativeAmountIsRefused() {
        assertRefused(PricingException.Error.AMOUNT_MINOR_NEGATIVE, () -> new Money(-1, PLN));
    }

    @Test
    void aValidityIsHalfOpen() {
        assertThat(until(MAY_1, JULY_1).isActiveAt(MAY_1)).isTrue();
        assertThat(until(MAY_1, JULY_1).isActiveAt(JULY_1)).isFalse();
        assertThat(openFrom(MAY_1).isOpenEnded()).isTrue();
    }

    @Test
    void aValidityMustEndAfterItStarts() {
        assertRefused(PricingException.Error.VALIDITY_RANGE_EMPTY, () -> until(JULY_1, JULY_1));
    }

    @Test
    void anEntryOrDiscountIdMustCarryAValue() {
        assertRefused(PricingException.Error.PRICE_ID_MISSING, () -> new PriceId(" "));
        assertRefused(PricingException.Error.DISCOUNT_ID_MISSING, () -> new DiscountId(""));
    }

    @Test
    void anActivePriceIsNeverIncomplete() {
        assertRefused(PricingException.Error.ACTIVE_PRICE_INCOMPLETE, () -> ActivePrice.priced(null));
        assertThat(ActivePrice.absent().amount()).isNull();
    }

    @Test
    void aDiscountPolicyNeedsBothOfItsVariants() {
        assertRefused(PricingException.Error.DISCOUNT_POLICY_INCOMPLETE,
                () -> ActivePriceCalculation.DiscountPolicy.of(null,
                        ActivePriceCalculation.DiscountPolicy.Stacking.STACKED));
    }

    @Test
    void percentageDiscountIsBoundedToOneHundredPercent() {
        assertRefused(PricingException.Error.BASIS_POINTS_OUT_OF_RANGE, () -> new Discount.Kind.Percentage(0));
        assertRefused(PricingException.Error.BASIS_POINTS_OUT_OF_RANGE, () -> new Discount.Kind.Percentage(10001));
        assertThat(new Discount.Kind.Percentage(10000).basisPoints()).isEqualTo(10000);
    }
}
