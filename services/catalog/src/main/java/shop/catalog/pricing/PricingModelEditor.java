package shop.catalog.pricing;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Element 03 — the pricing model of one offer: the price series per currency, the discounts, and the events that say
 * what changed. Every price edit closes the predecessor in the same command and appends a new entry (RULE-2, RULE-3),
 * a started entry can only be closed by a successor and never removed (RULE-7, RULE-8), and activation is a command
 * driven by an outside poll — the model records the fact and owns no timer (RULE-4, RULE-9).
 *
 * <p>RULE-4 is about activation, not about bookkeeping: a human's decision to schedule an entry is an in-context
 * {@code *Scheduled} fact, while nothing leaves the context and no {@code *Activated} fact exists before the entry's
 * {@code validFrom}. Removing a scheduled entry reopens the predecessor it had closed, so a scheduling that never took
 * effect leaves no trace in the effective series.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class PricingModelEditor {

    final String offerId;
    final List<PricingEvent> events;
    private List<PriceEntry> prices;
    private List<Discount> discounts;

    /** An empty model records no pricing fact, so the factory emits no event — 03 lists no "opened" fact. */
    static PricingModelEditor newPricingModelFor(String offerId) {
        Objects.requireNonNull(offerId, "offerId");
        return new PricingModelEditor(offerId, new ArrayList<>(), List.of(), List.of());
    }

    void schedulePrice(PriceChange change, Instant now) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(now, "now");
        ensurePriceIsNew(change.priceId());

        this.prices = pricesWithAppendedEntry(change, now);
        events.add(new PricingEvent.PriceScheduled(offerId, change.priceId(), change.price(), change.validity()));
    }

    void removePrice(PriceId priceId, Instant now) {
        Objects.requireNonNull(priceId, "priceId");
        Objects.requireNonNull(now, "now");
        PriceEntry removed = priceEntry(priceId);
        ensurePriceHasNotStarted(removed, now);

        this.prices = pricesWithout(removed);
        events.add(new PricingEvent.PriceRemoved(offerId, priceId));
    }

    void scheduleDiscount(DiscountChange change, Instant now) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(now, "now");
        ensureDiscountIsNew(change.discountId());
        ensureDiscountDoesNotStartInThePast(change, now);
        ensureDiscountCurrencyIsPriced(change);

        this.discounts = discountsWithAppendedEntry(change);
        events.add(new PricingEvent.DiscountScheduled(offerId, change.discountId(), change.kind(), change.validity()));
    }

    void removeDiscount(DiscountId discountId, Instant now) {
        Objects.requireNonNull(discountId, "discountId");
        Objects.requireNonNull(now, "now");
        ensureDiscountHasNotStarted(discount(discountId), now);

        this.discounts = discounts.stream().filter(discount -> !discount.discountId().equals(discountId)).toList();
        events.add(new PricingEvent.DiscountRemoved(offerId, discountId));
    }

    void activateDueEntries(Instant now) {
        Objects.requireNonNull(now, "now");
        activateDuePrices(now);
        activateDueDiscounts(now);
    }

    PricingModel toPricingModel(Instant now) {
        Objects.requireNonNull(now, "now");
        return new PricingModel(currentPrices(now), scheduledPrices(now), historicPrices(now), discounts);
    }

    private List<PriceEntry> pricesWithAppendedEntry(PriceChange change, Instant now) {
        Validity validity = change.validity();
        ensurePriceDoesNotStartInThePast(change, now);

        List<PriceEntry> appended = new ArrayList<>(prices.stream().map(entry -> closedBefore(entry, change)).toList());
        appended.add(PriceEntry.scheduled(change.priceId(), change.price(), validity));
        ensureOneOpenPricePerCurrency(appended);
        return List.copyOf(appended);
    }

    private static PriceEntry closedBefore(PriceEntry entry, PriceChange change) {
        Instant newStart = change.validity().validFrom();
        boolean isPredecessor = entry.price().isIn(change.price().currency())
                && entry.validity().validFrom().isBefore(newStart)
                && entry.isActiveAt(newStart);
        return isPredecessor ? entry.closedAt(newStart) : entry;
    }

    private List<Discount> discountsWithAppendedEntry(DiscountChange change) {
        List<Discount> appended = new ArrayList<>(discounts);
        appended.add(Discount.scheduled(change.discountId(), change.kind(), change.validity()));
        return appended.stream()
                .sorted(Comparator.comparing(discount -> discount.validity().validFrom()))
                .toList();
    }

    private void activateDuePrices(Instant now) {
        List<PriceEntry> due = ActivationDue.decideDuePrices(prices, now);
        if (due.isEmpty()) {
            return;
        }
        List<PriceId> dueIds = due.stream().map(PriceEntry::priceId).toList();
        this.prices = prices.stream()
                .map(entry -> dueIds.contains(entry.priceId()) ? entry.markedActivated() : entry)
                .toList();
        for (PriceEntry entry : due) {
            events.add(new PricingEvent.PriceActivated(offerId, entry.price(), entry.validity().validFrom(), now));
        }
    }

    private void activateDueDiscounts(Instant now) {
        List<Discount> due = ActivationDue.decideDueDiscounts(discounts, now);
        if (due.isEmpty()) {
            return;
        }
        List<DiscountId> dueIds = due.stream().map(Discount::discountId).toList();
        this.discounts = discounts.stream()
                .map(discount -> dueIds.contains(discount.discountId()) ? discount.markedActivated() : discount)
                .toList();
        for (Discount discount : due) {
            events.add(new PricingEvent.DiscountActivated(
                    offerId, discount.discountId(), discount.validity().validFrom(), now));
        }
    }

    private List<PriceEntry> pricesWithout(PriceEntry removed) {
        return prices.stream()
                .filter(entry -> !entry.priceId().equals(removed.priceId()))
                .map(entry -> reopenedAfterRemoval(entry, removed))
                .toList();
    }

    private static PriceEntry reopenedAfterRemoval(PriceEntry entry, PriceEntry removed) {
        boolean isPredecessor = entry.price().isIn(removed.price().currency())
                && Objects.equals(entry.validity().validUntil(), removed.validity().validFrom());
        return isPredecessor ? entry.reopenedUntil(removed.validity().validUntil()) : entry;
    }

    private List<PriceEntry> currentPrices(Instant now) {
        return prices.stream().filter(entry -> entry.isActiveAt(now)).toList();
    }

    private List<PriceEntry> scheduledPrices(Instant now) {
        return prices.stream().filter(entry -> entry.isScheduledAt(now)).toList();
    }

    private List<PriceEntry> historicPrices(Instant now) {
        return prices.stream().filter(entry -> entry.isHistoricAt(now)).toList();
    }

    private PriceEntry priceEntry(PriceId priceId) {
        return prices.stream()
                .filter(entry -> entry.priceId().equals(priceId))
                .findFirst()
                .orElseThrow(() -> new PricingException(PricingException.Error.PRICE_NOT_FOUND, priceId.value()));
    }

    private void ensurePriceIsNew(PriceId priceId) {
        if (prices.stream().anyMatch(entry -> entry.priceId().equals(priceId))) {
            throw new PricingException(PricingException.Error.PRICE_ID_DUPLICATED, priceId.value());
        }
    }

    private static void ensurePriceDoesNotStartInThePast(PriceChange change, Instant now) {
        if (change.validity().validFrom().isBefore(now)) {
            throw new PricingException(
                    PricingException.Error.PRICE_VALID_FROM_IN_PAST,
                    change.priceId().value(), change.validity().validFrom(), now);
        }
    }

    private static void ensurePriceHasNotStarted(PriceEntry entry, Instant now) {
        if (entry.hasStartedAt(now)) {
            throw new PricingException(
                    PricingException.Error.PRICE_ALREADY_STARTED, entry.priceId().value(), now);
        }
    }

    private static void ensureOneOpenPricePerCurrency(List<PriceEntry> candidate) {
        candidate.stream()
                .map(entry -> entry.price().currency())
                .distinct()
                .forEach(currency -> ensureCurrencySeriesIsDisjoint(candidate, currency));
    }

    private static void ensureCurrencySeriesIsDisjoint(List<PriceEntry> candidate, String currency) {
        List<PriceEntry> series = candidate.stream()
                .filter(entry -> entry.price().isIn(currency))
                .sorted(Comparator.comparing(entry -> entry.validity().validFrom()))
                .toList();
        for (int index = 1; index < series.size(); index++) {
            ensureNoOverlap(series.get(index - 1), series.get(index));
        }
    }

    private static void ensureNoOverlap(PriceEntry predecessor, PriceEntry entry) {
        if (predecessor.isActiveAt(entry.validity().validFrom())) {
            throw new PricingException(
                    PricingException.Error.PRICE_RANGE_OVERLAPS,
                    entry.priceId().value(), predecessor.priceId().value());
        }
    }

    private void ensureDiscountIsNew(DiscountId discountId) {
        if (discounts.stream().anyMatch(discount -> discount.discountId().equals(discountId))) {
            throw new PricingException(PricingException.Error.DISCOUNT_ID_DUPLICATED, discountId.value());
        }
    }

    private static void ensureDiscountDoesNotStartInThePast(DiscountChange change, Instant now) {
        if (change.validity().validFrom().isBefore(now)) {
            throw new PricingException(
                    PricingException.Error.DISCOUNT_VALID_FROM_IN_PAST,
                    change.discountId().value(), change.validity().validFrom(), now);
        }
    }

    private Discount discount(DiscountId discountId) {
        return discounts.stream()
                .filter(discount -> discount.discountId().equals(discountId))
                .findFirst()
                .orElseThrow(() -> new PricingException(PricingException.Error.DISCOUNT_NOT_FOUND, discountId.value()));
    }

    private static void ensureDiscountHasNotStarted(Discount discount, Instant now) {
        if (discount.validity().hasStartedAt(now)) {
            throw new PricingException(
                    PricingException.Error.DISCOUNT_ALREADY_STARTED, discount.discountId().value(), now);
        }
    }

    private void ensureDiscountCurrencyIsPriced(DiscountChange change) {
        boolean pricedCurrencyExists = prices.stream()
                .map(entry -> entry.price().currency())
                .distinct()
                .anyMatch(currency -> change.kind().appliesTo(currency));
        if (!prices.isEmpty() && !pricedCurrencyExists) {
            throw new PricingException(
                    PricingException.Error.DISCOUNT_CURRENCY_NOT_PRICED, change.discountId().value());
        }
    }
}
