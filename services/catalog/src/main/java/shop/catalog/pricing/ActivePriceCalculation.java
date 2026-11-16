package shop.catalog.pricing;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Element 10 — the price a customer pays for one offer in one currency at one instant.
 *
 * <pre>
 * fixed  = sum(fixed amounts of the active discounts in this currency)
 * pct    = min(10000, sum(basis points of the active percentage discounts))
 * result = roundHalfUp(max(0, price - fixed) * (10000 - pct) / 10000)
 * </pre>
 *
 * <p>One half-up rounding, at the very end; no intermediate rounding. No open price entry answers {@link ActivePrice}
 * {@code ABSENT}, never {@code 0}. The order of the two discount families and their stacking are the D3 default, with
 * the two documented variants available as {@link DiscountPolicy} values (10: {@code catalog.pricing.order},
 * {@code catalog.pricing.stacking}).
 */
record ActivePriceCalculation(DiscountPolicy policy) {

    private static final int FULL_PERCENT = 10000;
    private static final int ROUNDING_HALF = FULL_PERCENT / 2;

    ActivePriceCalculation {
        Objects.requireNonNull(policy, "policy");
    }

    static ActivePriceCalculation defaultCalculation() {
        return new ActivePriceCalculation(DiscountPolicy.defaultPolicy());
    }

    static ActivePriceCalculation withPolicy(DiscountPolicy policy) {
        return new ActivePriceCalculation(policy);
    }

    /**
     * Precondition: at most one open entry per currency — 03 RULE-3, guarded by {@link PricingModelEditor}. Should
     * rehydrated data ever break it, the first match answers rather than failing: a policy answers, it never throws.
     */
    ActivePrice calculateActivePrice(List<PriceEntry> prices, List<Discount> discounts, Instant at, String currency) {
        return openPriceEntry(prices, currency, at)
                .map(entry -> ActivePrice.priced(
                        reducedPrice(entry.price(), applyableDiscounts(discounts, currency, at))))
                .orElseGet(ActivePrice::absent);
    }

    private static Optional<PriceEntry> openPriceEntry(List<PriceEntry> prices, String currency, Instant at) {
        return prices.stream()
                .filter(entry -> entry.isActiveIn(currency, at))
                .findFirst();
    }

    private List<Discount> applyableDiscounts(List<Discount> discounts, String currency, Instant at) {
        List<Discount> active = discounts.stream()
                .filter(discount -> discount.isActiveIn(currency, at))
                .toList();
        return policy.stacking() == DiscountPolicy.Stacking.EXCLUSIVE ? latestOf(active) : active;
    }

    private static List<Discount> latestOf(List<Discount> discounts) {
        return discounts.stream()
                .max(Comparator.comparing(discount -> discount.validity().validFrom()))
                .map(List::of)
                .orElseGet(List::of);
    }

    private Money reducedPrice(Money price, List<Discount> applyable) {
        int fixedMinor = fixedMinor(applyable);
        int percentage = percentageBasisPoints(applyable);
        int amountMinor = policy.order() == DiscountPolicy.Order.PERCENT_FIRST
                ? reducedByPercentageThenFixed(price.amountMinor(), fixedMinor, percentage)
                : reducedByFixedThenPercentage(price.amountMinor(), fixedMinor, percentage);
        return Money.ofMinor(amountMinor, price.currency());
    }

    private static int reducedByFixedThenPercentage(int amountMinor, int fixedMinor, int percentage) {
        return roundedByPercentage(Math.max(0, amountMinor - fixedMinor), percentage);
    }

    private static int reducedByPercentageThenFixed(int amountMinor, int fixedMinor, int percentage) {
        return Math.max(0, roundedByPercentage(amountMinor, percentage) - fixedMinor);
    }

    private static int roundedByPercentage(int amountMinor, int percentageBasisPoints) {
        long scaled = (long) amountMinor * (FULL_PERCENT - percentageBasisPoints);
        return (int) ((scaled + ROUNDING_HALF) / FULL_PERCENT);
    }

    private static int fixedMinor(List<Discount> applyable) {
        return applyable.stream().mapToInt(discount -> discount.kind().fixedMinor()).sum();
    }

    private static int percentageBasisPoints(List<Discount> applyable) {
        int summed = applyable.stream().mapToInt(discount -> discount.kind().basisPoints()).sum();
        return Math.min(FULL_PERCENT, summed);
    }

    /** The two variants of 10 as values: which discount family goes first, and whether discounts stack. */
    record DiscountPolicy(Order order, Stacking stacking) {

        DiscountPolicy {
            if (order == null || stacking == null) {
                throw new PricingException(PricingException.Error.DISCOUNT_POLICY_INCOMPLETE);
            }
        }

        static DiscountPolicy defaultPolicy() {
            return new DiscountPolicy(Order.FIXED_FIRST, Stacking.STACKED);
        }

        static DiscountPolicy of(Order order, Stacking stacking) {
            return new DiscountPolicy(order, stacking);
        }

        enum Order {
            FIXED_FIRST, PERCENT_FIRST
        }

        enum Stacking {
            STACKED, EXCLUSIVE
        }
    }
}
