package shop.catalog.pricing;

import java.util.Objects;

/** One requested discount edit: the discount to append and the range it covers (03 RULE-5). */
record DiscountChange(DiscountId discountId, Discount.Kind kind, Validity validity) {

    DiscountChange {
        Objects.requireNonNull(discountId, "discountId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(validity, "validity");
    }
}
