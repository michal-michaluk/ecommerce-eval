package shop.shopping.basket;

import java.util.Locale;
import java.util.Objects;

import shop.shopping.kernel.Money;

/**
 * The one currency a basket holds, decided by the deployment rather than by a literal in the code (element 03 RULE-2, D21).
 *
 * <p>An empty basket has no item to take a currency from and the API still reports one (element 07, {@code basket} step 2 of
 * the contract), so the currency of a freshly auto-created basket is configuration. It is decided once per basket: an offer
 * priced in another currency is refused, never converted (RULE-2).
 *
 * <p>The configured code is a deployment value and is normalised here, once: ISO-4217 codes are case-insensitive, so
 * {@code pln} and {@code PLN} name the same currency, and a basket opened in {@code "pln"} would refuse every {@code PLN}
 * amount it was offered. The normalised code is what {@link Money#zero(String)} accepts and what the basket stores.
 *
 * @param code the ISO-4217 code a new basket is opened in, in its canonical upper-case form
 */
record BasketCurrency(String code) {

    BasketCurrency {
        String requested = Objects.requireNonNull(code, "code").trim().toUpperCase(Locale.ROOT);
        code = Money.zero(requested).currency();
    }
}
