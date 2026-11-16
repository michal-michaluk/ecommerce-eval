package shop.catalog.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.MAY_1;
import static shop.catalog.pricing.PricingFixture.clockAt;
import static shop.catalog.pricing.PricingFixture.fixedDiscount;
import static shop.catalog.pricing.PricingFixture.openPercentageDiscount;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.priceEntry;

class ActivationDueTest {

    @Test
    void decidesOnlyStartedEntriesWithoutAnActivationMarker() {
        PriceEntry started = priceEntry("P-1", pln(30989), MAY_1, null);
        PriceEntry activated = priceEntry("P-2", pln(29900), MAY_1, null).markedActivated();
        PriceEntry future = priceEntry("P-3", pln(28900), JULY_1, null);

        List<PriceEntry> due = ActivationDue.decideDuePrices(
                List.of(activated, future, started), clockAt(JUNE_1).instant());

        assertThat(due).containsExactly(started);
    }

    @Test
    void decidesDuePricesInValidFromOrder() {
        PriceEntry later = priceEntry("P-2", pln(29900), JUNE_1, null);
        PriceEntry earlier = priceEntry("P-1", pln(30989), MAY_1, null);

        List<PriceEntry> due = ActivationDue.decideDuePrices(List.of(later, earlier), clockAt(JUNE_1).instant());

        assertThat(due).containsExactly(earlier, later);
    }

    @Test
    void decidesDiscountsByTheSameRule() {
        Discount due = openPercentageDiscount("D-1", 1000, MAY_1);
        Discount future = fixedDiscount("D-2", pln(2000), JULY_1, null);
        Discount activated = fixedDiscount("D-3", pln(3000), MAY_1, null).markedActivated();

        List<Discount> dueDiscounts = ActivationDue.decideDueDiscounts(
                List.of(future, activated, due), clockAt(JUNE_1).instant());

        assertThat(dueDiscounts).containsExactly(due);
    }

    @Test
    void anEntryIsDueExactlyAtItsValidFrom() {
        PriceEntry starting = priceEntry("P-1", pln(30989), MAY_1, JULY_1);

        List<PriceEntry> due = ActivationDue.decideDuePrices(List.of(starting), clockAt(MAY_1).instant());

        assertThat(due).containsExactly(starting);
    }
}
