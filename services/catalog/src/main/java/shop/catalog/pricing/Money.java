package shop.catalog.pricing;

import java.util.Currency;
import java.util.Objects;

/**
 * RULE-1 — an integer amount in minor units plus its ISO 4217 currency (D1). The scale is the currency's own
 * ({@code PLN} 2, {@code JPY} 0) and is derived on read, so no amount is ever stored as a decimal or a float.
 */
record Money(int amountMinor, String currency) {

    Money {
        Objects.requireNonNull(currency, "currency");
        if (amountMinor < 0) {
            throw new PricingException(PricingException.Error.AMOUNT_MINOR_NEGATIVE, amountMinor);
        }
        if (!isIsoCurrency(currency)) {
            throw new PricingException(PricingException.Error.CURRENCY_UNKNOWN, currency);
        }
    }

    static Money ofMinor(int amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    int scale() {
        return Currency.getInstance(currency).getDefaultFractionDigits();
    }

    boolean isIn(String otherCurrency) {
        return currency.equals(otherCurrency);
    }

    private static boolean isIsoCurrency(String code) {
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException unknownCode) {
            return false;
        }
    }
}
