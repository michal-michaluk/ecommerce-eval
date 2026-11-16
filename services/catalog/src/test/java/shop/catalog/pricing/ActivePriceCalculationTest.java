package shop.catalog.pricing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.pricing.PricingFixture.EUR;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.JUNE_20;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.JULY_8;
import static shop.catalog.pricing.PricingFixture.MAY_1;
import static shop.catalog.pricing.PricingFixture.PLN;
import static shop.catalog.pricing.PricingFixture.PRICE_30989;
import static shop.catalog.pricing.PricingFixture.clockAt;
import static shop.catalog.pricing.PricingFixture.eur;
import static shop.catalog.pricing.PricingFixture.fixedDiscount;
import static shop.catalog.pricing.PricingFixture.openPercentageDiscount;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.priceEntry;

class ActivePriceCalculationTest {

    final ActivePriceCalculation calculation = ActivePriceCalculation.defaultCalculation();

    @Test
    void s1PriceWithoutAnyDiscountIsTheListedPrice() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989), List.of(), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(30989)));
    }

    @Test
    void s2FixedDiscountIsSubtractedFromTheListedPrice() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", pln(2000), MAY_1, null)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(28989)));
    }

    @Test
    void s3PercentageDiscountIsAppliedToTheListedPrice() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(openPercentageDiscount("D-1", 1000, MAY_1)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(27890)));
    }

    @Test
    void s4FixedIsSubtractedFirstAndThePercentageAppliedAfterwards() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", pln(2000), MAY_1, null), openPercentageDiscount("D-2", 1000, MAY_1)),
                at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(26090)));
    }

    @Test
    void s5AFixedDiscountLargerThanThePriceFloorsTheResultAtZero() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", pln(40000), MAY_1, null)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(0)));
    }

    @Test
    void s6StackedPercentagesAreCappedAtOneHundredPercent() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(openPercentageDiscount("D-1", 6000, MAY_1), openPercentageDiscount("D-2", 6000, MAY_1)),
                at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(0)));
    }

    @Test
    void s7ADiscountOutsideItsOwnRangeDoesNotApply() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", pln(2000), JULY_1, JULY_8)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(30989)));
    }

    @Test
    void s8NoOpenPriceEntryAnswersAbsentAndNeverZero() {
        ActivePrice price = calculation.calculateActivePrice(List.of(), List.of(), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.absent());
        assertThat(price.isAbsent()).isTrue();
    }

    @Test
    void s9APriceAppliesAtExactlyItsValidFrom() {
        ActivePrice price = calculation.calculateActivePrice(
                List.of(priceEntry("P-1", pln(30989), MAY_1, JULY_1)), List.of(), at(MAY_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(30989)));
    }

    @Test
    void s10APriceNoLongerAppliesAtExactlyItsValidUntil() {
        ActivePrice price = calculation.calculateActivePrice(
                List.of(priceEntry("P-1", pln(30989), MAY_1, JULY_1)), List.of(), at(JULY_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.absent());
    }

    @Test
    void percentFirstVariantAppliesThePercentageBeforeTheFixedAmount() {
        ActivePriceCalculation percentFirst = ActivePriceCalculation.withPolicy(
                ActivePriceCalculation.DiscountPolicy.of(
                        ActivePriceCalculation.DiscountPolicy.Order.PERCENT_FIRST,
                        ActivePriceCalculation.DiscountPolicy.Stacking.STACKED));

        ActivePrice price = percentFirst.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", pln(2000), MAY_1, null), openPercentageDiscount("D-2", 1000, MAY_1)),
                at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(25890)));
    }

    @Test
    void exclusiveStackingAppliesOnlyTheDiscountWithTheLatestValidFrom() {
        ActivePriceCalculation exclusive = ActivePriceCalculation.withPolicy(
                ActivePriceCalculation.DiscountPolicy.of(
                        ActivePriceCalculation.DiscountPolicy.Order.FIXED_FIRST,
                        ActivePriceCalculation.DiscountPolicy.Stacking.EXCLUSIVE));
        List<Discount> discounts = List.of(openPercentageDiscount("D-1", 1000, MAY_1),
                fixedDiscount("D-2", pln(5000), JUNE_20, null));

        assertThat(exclusive.calculateActivePrice(List.of(PRICE_30989), discounts, at(JUNE_20), PLN))
                .isEqualTo(ActivePrice.priced(pln(25989)));
        assertThat(calculation.calculateActivePrice(List.of(PRICE_30989), discounts, at(JUNE_20), PLN))
                .isEqualTo(ActivePrice.priced(pln(23390)));
    }

    @Test
    void roundingIsHalfUpAndHappensOnceAtTheVeryEnd() {
        ActivePrice price = calculation.calculateActivePrice(
                List.of(priceEntry("P-1", pln(2), MAY_1, null)),
                List.of(openPercentageDiscount("D-1", 2500, MAY_1)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(2)));
    }

    @Test
    void aFixedDiscountOnlyCountsInItsOwnCurrency() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", eur(2000), MAY_1, null)), at(JUNE_1), PLN);

        assertThat(price).isEqualTo(ActivePrice.priced(pln(30989)));
    }

    @Test
    void aCurrencyWithoutAnOpenPriceHasNoPriceAtAll() {
        ActivePrice price = calculation.calculateActivePrice(List.of(PRICE_30989),
                List.of(fixedDiscount("D-1", eur(2000), MAY_1, null)), at(JUNE_1), EUR);

        assertThat(price).isEqualTo(ActivePrice.absent());
    }

    private static Instant at(Instant instant) {
        return clockAt(instant).instant();
    }
}
