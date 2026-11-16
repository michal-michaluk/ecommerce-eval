package shop.catalog.pricing;

import java.util.Objects;

/** One requested price edit: the entry to append and the range it covers — future or starting now (03 RULE-7). */
record PriceChange(PriceId priceId, Money price, Validity validity) {

    PriceChange {
        Objects.requireNonNull(priceId, "priceId");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(validity, "validity");
    }
}
