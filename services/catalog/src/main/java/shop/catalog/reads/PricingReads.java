package shop.catalog.reads;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What the pricing view of 16 renders for one offer in one currency: the current price, the entries that start later, the history a
 * change closed, the discounts in force, upcoming and gone, and 10's answer for the date the reader picked.
 *
 * <p>Three lists come from one stored series and are split here, because "current", "scheduled" and "history" are statements about
 * the clock: an entry scheduled for tomorrow is today's {@code scheduled} and tomorrow's {@code current} without any event, so a
 * stored partition would be wrong by morning. Only an activation is a fact, and pricing publishes it.
 *
 * <p>{@code effectivePreview} is the one component that is not a copy of the projected row: it is 10's calculation for the reader's
 * date, asked through {@link EffectivePrices}, so the effect of stacking (D3) is visible before anything is saved (RULE-4). An offer
 * with no open price for the currency omits it rather than showing {@code 0}, which is a real price.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record PricingReads(
        String offerId,
        String currency,
        List<PricedItem> current,
        List<PricedItem> scheduled,
        List<PricedItem> history,
        Discounts discounts,
        Preview effectivePreview) {

    PricingReads {
        current = List.copyOf(current);
        scheduled = List.copyOf(scheduled);
        history = List.copyOf(history);
    }

    /**
     * The screen of one currency, or {@code null} when the stored row cannot describe it: a currency the offer never priced has no
     * current price to show, and a discount alone discounts nothing (03 RULE-5 refuses one in an unpriced currency).
     */
    static PricingReads ofNullable(
            PricingReadsProjection.PricingRow row, String currency, Instant previewAt, Instant now,
            Optional<EffectivePrices.Result> preview) {
        List<PricedItem> prices = row.prices().items().stream()
                .filter(price -> currency.equals(price.currency()))
                .toList();
        if (prices.isEmpty()) {
            return null;
        }
        return new PricingReads(row.offerId(), currency,
                prices.stream().filter(price -> price.isActiveAt(now)).toList(),
                prices.stream().filter(price -> price.isScheduledAt(now))
                        .sorted(Comparator.comparing(PricedItem::validFrom)).toList(),
                prices.stream().filter(price -> price.isHistoricAt(now))
                        .sorted(Comparator.comparing(PricedItem::validFrom).reversed()).toList(),
                Discounts.of(row.discounts().items(), currency, now),
                preview.map(amount -> new Preview(previewAt, amount.amountMinor(), amount.currency())).orElse(null));
    }

    /** The three discount lists of 16, in the order the screen renders them. */
    record Discounts(List<DiscountItem> active, List<DiscountItem> scheduled, List<DiscountItem> expired) {

        Discounts {
            active = List.copyOf(active);
            scheduled = List.copyOf(scheduled);
            expired = List.copyOf(expired);
        }

        static Discounts of(List<DiscountItem> stored, String currency, Instant now) {
            List<DiscountItem> inCurrency = stored.stream().filter(discount -> discount.appliesTo(currency)).toList();
            return new Discounts(
                    inCurrency.stream().filter(discount -> discount.isActiveAt(now)).toList(),
                    inCurrency.stream().filter(discount -> discount.isScheduledAt(now)).toList(),
                    inCurrency.stream().filter(discount -> discount.isExpiredAt(now)).toList());
        }
    }

    /** 10's answer for the date the reader picked: the amount in minor units, with the currency it is expressed in (D1). */
    record Preview(Instant at, int amountMinor, String currency) {
    }
}
