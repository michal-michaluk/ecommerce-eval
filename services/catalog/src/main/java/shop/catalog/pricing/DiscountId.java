package shop.catalog.pricing;

import java.util.Objects;

/** The identity of one discount — the value a caller quotes to remove that discount (03, 12). */
record DiscountId(String value) {

    DiscountId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PricingException(PricingException.Error.DISCOUNT_ID_MISSING);
        }
    }
}
