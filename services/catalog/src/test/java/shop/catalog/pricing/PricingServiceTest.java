package shop.catalog.pricing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.JULY_15;
import static shop.catalog.pricing.PricingFixture.JULY_8;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;
import static shop.catalog.pricing.PricingFixture.clockAt;
import static shop.catalog.pricing.PricingFixture.discountId;
import static shop.catalog.pricing.PricingFixture.openPriceChange;
import static shop.catalog.pricing.PricingFixture.percentageChange;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.priceId;

/** The orchestration only: load, one command, save — proven against an in-memory port, no container (testing.md). */
class PricingServiceTest {

    final Map<String, PricingModelEditor> models = new HashMap<>();
    final PricingService service = serviceAt(JUNE_1);

    @Test
    void opensTheModelOfAnOfferThatHasNoneYet() {
        PricingModel scheduled = service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JUNE_1));

        assertThat(models).containsKey(OFFER_ID);
        assertThat(scheduled.current()).extracting(PriceEntry::priceId).containsExactly(priceId("P-1"));
    }

    @Test
    void theNextCommandLoadsWhatThePreviousOneSaved() {
        service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JUNE_1));
        service.schedulePrice(OFFER_ID, openPriceChange("P-2", pln(29900), JULY_1));

        PricingModel later = serviceAt(JULY_15).schedulePrice(OFFER_ID, openPriceChange("P-3", pln(27900), JULY_15));

        assertThat(later.current()).extracting(PriceEntry::priceId).containsExactly(priceId("P-3"));
        assertThat(later.history()).extracting(PriceEntry::priceId).containsExactly(priceId("P-1"), priceId("P-2"));
    }

    @Test
    void removesAScheduledPrice() {
        service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JULY_1));

        PricingModel emptied = service.removePrice(OFFER_ID, priceId("P-1"));

        assertThat(emptied.scheduled()).isEmpty();
        assertThat(emptied.current()).isEmpty();
    }

    @Test
    void schedulesADiscountOnTheModelItTakesTheCurrencyFrom() {
        service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JUNE_1));

        PricingModel scheduled = service.scheduleDiscount(OFFER_ID, percentageChange("D-1", 1000, JULY_1, JULY_8));

        assertThat(scheduled.discounts()).extracting(Discount::discountId).containsExactly(discountId("D-1"));
    }

    @Test
    void removesAScheduledDiscount() {
        service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JUNE_1));
        service.scheduleDiscount(OFFER_ID, percentageChange("D-1", 1000, JULY_1, JULY_8));

        PricingModel removed = service.removeDiscount(OFFER_ID, discountId("D-1"));

        assertThat(removed.discounts()).isEmpty();
    }

    @Test
    void activatesWhatBecameDue() {
        service.schedulePrice(OFFER_ID, openPriceChange("P-1", pln(30989), JUNE_1));

        service.activateDue(OFFER_ID, JUNE_1);

        PricingModelEditorAssert.assertThat(models.get(OFFER_ID)).hasActivatedPrice(30989, JUNE_1, JUNE_1);
    }

    @Test
    void activationOfAnUnknownOfferStoresNothing() {
        service.activateDue("OFF-UNKNOWN", JUNE_1);

        assertThat(models).isEmpty();
    }

    private PricingService serviceAt(Instant instant) {
        return new PricingService(new FakePricingRepository(), clockAt(instant));
    }

    class FakePricingRepository implements PricingRepository {

        @Override
        public Optional<PricingModelEditor> get(String offerId) {
            return Optional.ofNullable(models.get(offerId));
        }

        @Override
        public void save(PricingModelEditor pricing) {
            models.put(pricing.offerId, pricing);
        }
    }
}
