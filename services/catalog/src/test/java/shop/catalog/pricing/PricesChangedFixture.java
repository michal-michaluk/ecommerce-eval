package shop.catalog.pricing;

import org.intellij.lang.annotations.Language;
import shop.catalog.tools.EventStream;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The pricing models 13 talks about, each named after the scenario it stages, plus the payloads as the JSON a consumer reads. A model is
 * always handed over as a running service leaves it: everything due before the scenario was activated and announced, and the fact buffer
 * is drained the way {@link NormalizingPricingRepository} drains it.
 */
public class PricesChangedFixture {

    public static PricesIntegration integrationOf(PricingRepository models, EventStream events) {
        return new PricesIntegration(models, events);
    }

    /** One price in force, one scheduled change and a percentage discount that outlives the change — S3 and S5. */
    public static PricingModelEditor modelWithAPriceChangeAndAPercentageDiscount() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.priceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1, PricingFixture.JULY_1), PricingFixture.APRIL_20);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-2", PricingFixture.pln(29900),
                PricingFixture.JULY_1), PricingFixture.APRIL_20);
        pricing.scheduleDiscount(PricingFixture.percentageChange("D-1", 1000,
                PricingFixture.JUNE_20, PricingFixture.JULY_8), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.JUNE_1);
    }

    /** The same change without any discount, so a price payload can be asserted without a side effect filling its discounts. */
    public static PricingModelEditor modelWithAPriceChangeAndNoDiscount() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.priceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1, PricingFixture.JULY_1), PricingFixture.APRIL_20);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-2", PricingFixture.pln(29900),
                PricingFixture.JULY_1), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.JUNE_1);
    }

    /** A price that starts later and nothing else — S2's subject, which must stay inside the context until it is due. */
    public static PricingModelEditor modelWithAScheduledPrice() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.JULY_1), PricingFixture.APRIL_20);
        return pricing;
    }

    /** A discount that starts later and an open price it will reduce — S4's subject, silent until it is due. */
    public static PricingModelEditor modelWithAScheduledDiscount() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1), PricingFixture.APRIL_20);
        pricing.scheduleDiscount(PricingFixture.percentageChange("D-1", 1000,
                PricingFixture.JULY_1, PricingFixture.JULY_8), PricingFixture.APRIL_20);
        return pricing;
    }

    /** Two open currencies and a discount bound to one of them: RULE-7 says only PLN may travel. */
    public static PricingModelEditor modelPricedInPlnAndEurWithAFixedPlnDiscount() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-1", PricingFixture.pln(29900),
                PricingFixture.MAY_1), PricingFixture.APRIL_20);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-2", PricingFixture.eur(6900),
                PricingFixture.MAY_1), PricingFixture.APRIL_20);
        pricing.scheduleDiscount(PricingFixture.fixedChange("D-1", PricingFixture.pln(2000),
                PricingFixture.JUNE_1, PricingFixture.JULY_8), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.MAY_1);
    }

    /** A price that started and ended while the service was down: the fact fires, nothing is open, so nothing may travel (RULE-7). */
    public static PricingModelEditor modelWithOnlyAnEndedPriceDue() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.priceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1, PricingFixture.JUNE_1), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.APRIL_20);
    }

    /** Two entries due at the same catch-up poll: the ended one must not be shipped, the open one must (RULE-7, RULE-8). */
    public static PricingModelEditor modelWithAnEndedAndAnOpenPriceDue() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.priceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1, PricingFixture.JUNE_1), PricingFixture.APRIL_20);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-2", PricingFixture.pln(29900),
                PricingFixture.JUNE_1), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.APRIL_20);
    }

    /** A discount that started and ended while the service was down: it changes nothing the shop is shown (RULE-3). */
    public static PricingModelEditor modelWithOnlyAnEndedDiscountDue() {
        PricingModelEditor pricing = PricingModelEditor.newPricingModelFor(PricingFixture.OFFER_ID);
        pricing.schedulePrice(PricingFixture.openPriceChange("P-1", PricingFixture.pln(30989),
                PricingFixture.MAY_1), PricingFixture.APRIL_20);
        pricing.scheduleDiscount(PricingFixture.percentageChange("D-1", 1000,
                PricingFixture.JUNE_1, PricingFixture.JULY_1), PricingFixture.APRIL_20);
        return inForceUntil(pricing, PricingFixture.MAY_1);
    }

    /** The facts the RULE-9 poll would publish at {@code at}, drained exactly as the persistence adapter drains them. */
    public static List<PricingEvent> activationFacts(PricingModelEditor pricing, Instant at) {
        pricing.activateDueEntries(at);
        return NormalizingPricingRepository.eventsEmittedFrom(pricing);
    }

    public static PricingEvent.PriceActivated priceActivated(List<PricingEvent> facts) {
        return facts.stream()
                .filter(PricingEvent.PriceActivated.class::isInstance)
                .map(PricingEvent.PriceActivated.class::cast)
                .findFirst()
                .orElseThrow();
    }

    public static List<PricingEvent.PriceActivated> priceActivations(List<PricingEvent> facts) {
        return facts.stream()
                .filter(PricingEvent.PriceActivated.class::isInstance)
                .map(PricingEvent.PriceActivated.class::cast)
                .toList();
    }

    public static PricingEvent.DiscountActivated discountActivated(List<PricingEvent> facts) {
        return facts.stream()
                .filter(PricingEvent.DiscountActivated.class::isInstance)
                .map(PricingEvent.DiscountActivated.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /** The price became 29900 PLN and a 10% discount is active next to it — the sample payload of 13. */
    @Language("JSON")
    public static String priceActivationJson() {
        return """
                {
                  "productId": "OFF-75423",
                  "currency": "PLN",
                  "price": { "amountMinor": 29900, "scale": 2 },
                  "discounts": [
                    { "kind": "percentage", "basisPoints": 1000, "validUntil": "2026-07-08T00:00:00Z" }
                  ],
                  "effectiveFrom": "2026-07-01T00:00:00Z"
                }
                """;
    }

    /** The same price, published because the discount started on 20 June: same amount, the discount's own effective date. */
    @Language("JSON")
    public static String discountActivationJson() {
        return """
                {
                  "productId": "OFF-75423",
                  "currency": "PLN",
                  "price": { "amountMinor": 29900, "scale": 2 },
                  "discounts": [
                    { "kind": "percentage", "basisPoints": 1000, "validUntil": "2026-07-08T00:00:00Z" }
                  ],
                  "effectiveFrom": "2026-06-20T00:00:00Z"
                }
                """;
    }

    /** A fixed discount in PLN, next to an open EUR entry the discount does not apply to (RULE-7). */
    @Language("JSON")
    public static String fixedPlnDiscountJson() {
        return """
                {
                  "productId": "OFF-75423",
                  "currency": "PLN",
                  "price": { "amountMinor": 29900, "scale": 2 },
                  "discounts": [
                    { "kind": "fixed", "amountMinor": 2000, "currency": "PLN", "validUntil": "2026-07-08T00:00:00Z" }
                  ],
                  "effectiveFrom": "2026-06-01T00:00:00Z"
                }
                """;
    }

    /** The catch-up that ships the entry which is open now, not the one that already ended (RULE-7, RULE-8). */
    @Language("JSON")
    public static String openPriceAfterCatchUpJson() {
        return """
                {
                  "productId": "OFF-75423",
                  "currency": "PLN",
                  "price": { "amountMinor": 29900, "scale": 2 },
                  "discounts": [],
                  "effectiveFrom": "2026-06-01T00:00:00Z"
                }
                """;
    }

    @Language("JSON")
    public static String priceWithoutDiscountJson() {
        return """
                {
                  "productId": "OFF-75423",
                  "currency": "PLN",
                  "price": { "amountMinor": 29900, "scale": 2 },
                  "discounts": [],
                  "effectiveFrom": "2026-07-01T00:00:00Z"
                }
                """;
    }

    /**
     * The model as a running service leaves it: everything due at {@code at} has already been activated and announced, so the scenario
     * that follows starts from an operator's model rather than from a fresh one where every entry is still waiting for its first poll.
     */
    public static PricingModelEditor inForceUntil(PricingModelEditor pricing, Instant at) {
        pricing.activateDueEntries(at);
        pricing.events.clear();
        return pricing;
    }

    /** The pricing model the adapter reads back from its own table, without a database — it drains like the real adapter. */
    public static final class Models implements PricingRepository {

        private final Map<String, PricingModelEditor> models = new HashMap<>();

        @Override
        public Optional<PricingModelEditor> get(String offerId) {
            return Optional.ofNullable(models.get(offerId));
        }

        @Override
        public void save(PricingModelEditor pricingModel) {
            NormalizingPricingRepository.eventsEmittedFrom(pricingModel);
            models.put(pricingModel.offerId, pricingModel);
        }
    }
}
