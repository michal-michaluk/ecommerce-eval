package shop.catalog.pricing;

import org.junit.jupiter.api.Test;
import shop.catalog.pricing.PricingEvent.DiscountActivated;
import shop.catalog.pricing.PricingEvent.DiscountScheduled;
import shop.catalog.pricing.PricingEvent.PriceActivated;
import shop.catalog.pricing.PricingEvent.PriceScheduled;

import java.time.Instant;

import static shop.catalog.pricing.PricingAssert.assertRefused;
import static shop.catalog.pricing.PricingFixture.APRIL_20;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.JUNE_15;
import static shop.catalog.pricing.PricingFixture.JUNE_20;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.JULY_15;
import static shop.catalog.pricing.PricingFixture.MAY_1;
import static shop.catalog.pricing.PricingFixture.clockAt;
import static shop.catalog.pricing.PricingFixture.discountId;
import static shop.catalog.pricing.PricingFixture.eur;
import static shop.catalog.pricing.PricingFixture.fixedChange;
import static shop.catalog.pricing.PricingFixture.openPriceChange;
import static shop.catalog.pricing.PricingFixture.percentageChange;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.priceChange;
import static shop.catalog.pricing.PricingFixture.priceId;
import static shop.catalog.pricing.PricingFixture.pricingModelOfInstanceA;
import static shop.catalog.pricing.PricingFixture.pricingModelWithOneOpenPrice;
import static shop.catalog.pricing.PricingModelEditorAssert.assertThat;

class PricingModelEditorTest {

    @Test
    void schedulingAFuturePriceClosesThePredecessorAndAppendsTheSuccessor() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        editor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), now);

        assertThat(editor).at(now).hasCurrentPrice("P-1", 30989).hasScheduledPrice("P-2", 29900);
        assertThat(editor).at(JULY_1).hasCurrentPrice("P-2", 29900).hasHistoricPriceClosedAt("P-1", JULY_1);
    }

    @Test
    void changingTheOpenPriceClosesItAtNowAndAppendsTheNewEntry() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        editor.schedulePrice(openPriceChange("P-2", pln(29900), now), now);

        assertThat(editor).at(now).hasCurrentPrice("P-2", 29900).hasHistoricPriceClosedAt("P-1", now);
    }

    @Test
    void aPriceCannotStartBeforeNow() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.PRICE_VALID_FROM_IN_PAST,
                () -> editor.schedulePrice(openPriceChange("P-2", pln(29900), MAY_1), now));
    }

    @Test
    void eachCurrencyKeepsItsOwnOpenEntry() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        editor.schedulePrice(openPriceChange("P-2", eur(7000), now), now);

        assertThat(editor).at(now).hasCurrentPrice("P-1", 30989).hasCurrentPrice("P-2", 7000);
    }

    @Test
    void aPriceStartingInsideAnAlreadyScheduledRangeIsRefused() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        editor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), APRIL_20);
        Instant now = clockAt(MAY_1).instant();

        assertRefused(PricingException.Error.PRICE_RANGE_OVERLAPS,
                () -> editor.schedulePrice(priceChange("P-3", pln(28900), JUNE_15, JULY_15), now));
    }

    @Test
    void aPriceIdIsPartOfTheModelOnlyOnce() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.PRICE_ID_DUPLICATED,
                () -> editor.schedulePrice(openPriceChange("P-1", pln(1000), now), now));
    }

    @Test
    void aStartedEntryCannotBeRemovedButAFutureEntryCan() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.PRICE_ALREADY_STARTED, () -> editor.removePrice(priceId("P-1"), now));
        editor.removePrice(priceId("P-2"), now);

        assertThat(editor).at(now).hasCurrentPrice("P-1", 30989).hasNoScheduledPrice();
    }

    @Test
    void schedulingIsSilentAndAnnouncesNoActivationBeforeItsValidFrom() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();
        editor.activateDueEntries(now);
        assertThat(editor).hasEvents(2);

        editor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), now);
        editor.activateDueEntries(now);

        assertThat(editor).hasEvents(3).hasLatestEvent(PriceScheduled.class);
    }

    @Test
    void activationEmitsOneEventPerDueEntryAndRunsOnce() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant now = clockAt(JULY_1).instant();
        editor.activateDueEntries(now);
        assertThat(editor).hasEvents(8).hasLatestEvent(DiscountActivated.class);

        editor.activateDueEntries(now);

        assertThat(editor).hasEvents(8);
    }

    @Test
    void aFutureEntryIsNotActivatedBeforeItsValidFrom() {
        PricingModelEditor editor = pricingModelOfInstanceA();

        editor.activateDueEntries(clockAt(JUNE_1).instant());
        assertThat(editor).hasEvents(5).hasLatestEvent(PriceActivated.class);

        editor.activateDueEntries(clockAt(JULY_1).instant());
        assertThat(editor).hasEvents(8).hasLatestEvent(DiscountActivated.class);
    }

    @Test
    void aDiscountIsScheduledRemovedAndAnnouncesItsSchedule() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        editor.scheduleDiscount(percentageChange("D-1", 1000, JUNE_20, JULY_1), now);
        assertThat(editor).at(now).hasDiscount("D-1").hasLatestEvent(DiscountScheduled.class);

        editor.removeDiscount(discountId("D-1"), now);
        assertThat(editor).at(now).hasNoDiscount("D-1");
    }

    @Test
    void aDiscountCannotStartBeforeNow() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.DISCOUNT_VALID_FROM_IN_PAST,
                () -> editor.scheduleDiscount(fixedChange("D-1", pln(2000), MAY_1, JULY_1), now));
    }

    @Test
    void aStartedDiscountCannotBeRemovedButAFutureDiscountCan() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant started = clockAt(JUNE_20).instant();

        assertRefused(PricingException.Error.DISCOUNT_ALREADY_STARTED,
                () -> editor.removeDiscount(discountId("D-1"), started));
        editor.removeDiscount(discountId("D-2"), started);

        assertThat(editor).at(started).hasNoDiscount("D-2");
    }

    @Test
    void removingAScheduledPriceReopensThePredecessorItHadClosed() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();
        editor.schedulePrice(openPriceChange("P-2", pln(29900), JULY_1), now);
        assertThat(editor).at(JULY_15).hasCurrentPrice("P-2", 29900);

        editor.removePrice(priceId("P-2"), now);

        assertThat(editor).at(JULY_15).hasCurrentPrice("P-1", 30989).hasNoScheduledPrice();
    }

    @Test
    void anUnknownPriceOrDiscountIsNotPartOfTheModel() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.PRICE_NOT_FOUND, () -> editor.removePrice(priceId("P-9"), now));
        assertRefused(PricingException.Error.DISCOUNT_NOT_FOUND,
                () -> editor.removeDiscount(discountId("D-9"), now));
    }

    @Test
    void aDiscountIdIsPartOfTheModelOnlyOnce() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.DISCOUNT_ID_DUPLICATED,
                () -> editor.scheduleDiscount(percentageChange("D-1", 500, JUNE_20, JULY_1), now));
    }

    @Test
    void aFixedDiscountIsBoundToACurrencyTheOfferPrices() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        assertRefused(PricingException.Error.DISCOUNT_CURRENCY_NOT_PRICED,
                () -> editor.scheduleDiscount(fixedChange("D-1", eur(2000), JUNE_20, JULY_1), now));
    }

    @Test
    void aFixedDiscountInAPricedCurrencyIsAccepted() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();
        Instant now = clockAt(JUNE_1).instant();

        editor.scheduleDiscount(fixedChange("D-1", pln(2000), JUNE_20, JULY_1), now);

        assertThat(editor).at(now).hasDiscount("D-1").hasEvents(2);
    }

    @Test
    void activationCarriesThePollMomentAndTheEffectiveDate() {
        PricingModelEditor editor = pricingModelOfInstanceA();
        Instant poll = clockAt(JULY_15).instant();

        editor.activateDueEntries(poll);

        assertThat(editor)
                .hasActivatedPrice(29900, JULY_1, poll)
                .hasActivatedDiscount("D-2", JULY_1, poll);
    }

    @Test
    void priceBoundariesAreHalfOpenAtEveryInstant() {
        PricingModelEditor editor = pricingModelWithOneOpenPrice();

        editor.schedulePrice(priceChange("P-2", pln(29900), JULY_1, JULY_15), clockAt(JUNE_1).instant());

        assertThat(editor).at(MAY_1).hasCurrentPrice("P-1", 30989).hasScheduledPrice("P-2", 29900);
        assertThat(editor).at(JULY_1).hasCurrentPrice("P-2", 29900).hasHistoricPriceClosedAt("P-1", JULY_1);
        assertThat(editor).at(JULY_15).hasNoCurrentPrice().hasHistoricPriceClosedAt("P-2", JULY_15);
    }
}
