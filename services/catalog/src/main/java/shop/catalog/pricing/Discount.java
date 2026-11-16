package shop.catalog.pricing;

import java.time.Instant;
import java.util.Objects;

/**
 * A promotion on the price of one offer (RULE-5). A percentage stores integer basis points within {@code 1..10000};
 * a fixed amount stores money in its own currency. A discount never touches a price entry — it is resolved at read
 * time by {@link ActivePriceCalculation} (RULE-6), and a fixed amount only counts against a price in its own currency.
 */
record Discount(DiscountId discountId, Discount.Kind kind, Validity validity, boolean activated) {

    Discount {
        Objects.requireNonNull(discountId, "discountId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(validity, "validity");
    }

    static Discount scheduled(DiscountId discountId, Kind kind, Validity validity) {
        return new Discount(discountId, kind, validity, false);
    }

    boolean isActiveIn(String currency, Instant at) {
        return kind.appliesTo(currency) && validity.isActiveAt(at);
    }

    boolean isDueAt(Instant at) {
        return !activated && validity.hasStartedAt(at);
    }

    Discount markedActivated() {
        return new Discount(discountId, kind, validity, true);
    }

    /** The two ways a discount reduces a price: which currencies it applies to, and how much it takes. */
    sealed interface Kind {

        boolean appliesTo(String currency);

        int fixedMinor();

        int basisPoints();

        record Percentage(int basisPoints) implements Kind {

            static final int MAX_BASIS_POINTS = 10000;

            public Percentage {
                if (basisPoints <= 0 || basisPoints > MAX_BASIS_POINTS) {
                    throw new PricingException(PricingException.Error.BASIS_POINTS_OUT_OF_RANGE, basisPoints);
                }
            }

            @Override
            public boolean appliesTo(String currency) {
                return true;
            }

            @Override
            public int fixedMinor() {
                return 0;
            }
        }

        record Fixed(Money amount) implements Kind {

            public Fixed {
                Objects.requireNonNull(amount, "amount");
            }

            @Override
            public boolean appliesTo(String currency) {
                return amount.isIn(currency);
            }

            @Override
            public int fixedMinor() {
                return amount.amountMinor();
            }

            @Override
            public int basisPoints() {
                return 0;
            }
        }
    }
}
