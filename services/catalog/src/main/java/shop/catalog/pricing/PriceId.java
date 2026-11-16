package shop.catalog.pricing;

import java.util.Objects;

/** The identity of one price entry — the value a caller quotes to edit or remove that entry (03, 12). */
record PriceId(String value) {

    PriceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new PricingException(PricingException.Error.PRICE_ID_MISSING);
        }
    }
}
