package shop.catalog.pricing;

import org.assertj.core.api.Assertions;

import java.time.Instant;
import java.util.List;

public class PricingModelEditorAssert {

    private final PricingModelEditor editor;
    private final Instant at;

    private PricingModelEditorAssert(PricingModelEditor editor, Instant at) {
        this.editor = editor;
        this.at = at;
    }

    public static PricingModelEditorAssert assertThat(PricingModelEditor actual) {
        Assertions.assertThat(actual).isNotNull();
        return new PricingModelEditorAssert(actual, null);
    }

    public PricingModelEditorAssert at(Instant moment) {
        return new PricingModelEditorAssert(editor, moment);
    }

    public PricingModelEditorAssert hasCurrentPrice(String priceId, int amountMinor) {
        Assertions.assertThat(amountsOf(pricing().current(), priceId)).containsExactly(amountMinor);
        return this;
    }

    public PricingModelEditorAssert hasNoCurrentPrice() {
        Assertions.assertThat(pricing().current()).isEmpty();
        return this;
    }

    public PricingModelEditorAssert hasScheduledPrice(String priceId, int amountMinor) {
        Assertions.assertThat(amountsOf(pricing().scheduled(), priceId)).containsExactly(amountMinor);
        return this;
    }

    public PricingModelEditorAssert hasNoScheduledPrice() {
        Assertions.assertThat(pricing().scheduled()).isEmpty();
        return this;
    }

    public PricingModelEditorAssert hasHistoricPriceClosedAt(String priceId, Instant validUntil) {
        Assertions.assertThat(pricing().history())
                .filteredOn(entry -> entry.priceId().value().equals(priceId))
                .extracting(entry -> entry.validity().validUntil())
                .containsExactly(validUntil);
        return this;
    }

    public PricingModelEditorAssert hasDiscount(String discountId) {
        Assertions.assertThat(pricing().discounts())
                .filteredOn(discount -> discount.discountId().value().equals(discountId))
                .hasSize(1);
        return this;
    }

    public PricingModelEditorAssert hasNoDiscount(String discountId) {
        Assertions.assertThat(pricing().discounts())
                .filteredOn(discount -> discount.discountId().value().equals(discountId))
                .isEmpty();
        return this;
    }

    public PricingModelEditorAssert hasEvents(int expected) {
        Assertions.assertThat(editor.events).hasSize(expected);
        return this;
    }

    public PricingModelEditorAssert hasLatestEvent(Class<? extends PricingEvent> expected) {
        Assertions.assertThat(editor.events).last().isInstanceOf(expected);
        return this;
    }

    public PricingModelEditorAssert hasActivatedPrice(int amountMinor, Instant effectiveFrom, Instant occurredAt) {
        Assertions.assertThat(eventsOf(PricingEvent.PriceActivated.class))
                .last()
                .satisfies(activated -> {
                    Assertions.assertThat(activated.price().amountMinor()).isEqualTo(amountMinor);
                    Assertions.assertThat(activated.effectiveFrom()).isEqualTo(effectiveFrom);
                    Assertions.assertThat(activated.occurredAt()).isEqualTo(occurredAt);
                });
        return this;
    }

    public PricingModelEditorAssert hasActivatedDiscount(
            String discountId, Instant effectiveFrom, Instant occurredAt) {
        Assertions.assertThat(eventsOf(PricingEvent.DiscountActivated.class))
                .last()
                .satisfies(activated -> {
                    Assertions.assertThat(activated.discountId().value()).isEqualTo(discountId);
                    Assertions.assertThat(activated.effectiveFrom()).isEqualTo(effectiveFrom);
                    Assertions.assertThat(activated.occurredAt()).isEqualTo(occurredAt);
                });
        return this;
    }

    private PricingModel pricing() {
        return editor.toPricingModel(at);
    }

    private <T extends PricingEvent> List<T> eventsOf(Class<T> type) {
        return editor.events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static List<Integer> amountsOf(List<PriceEntry> entries, String priceId) {
        return entries.stream()
                .filter(entry -> entry.priceId().value().equals(priceId))
                .map(entry -> entry.price().amountMinor())
                .toList();
    }
}
