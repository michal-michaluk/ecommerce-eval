package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The currency an empty basket is opened in: a deployment value normalised once, so a lower-case configuration and the
 * amounts it will be compared against are the same currency (element 03 RULE-2).
 */
class BasketCurrencyTest {

    @Test
    void readsALowerCaseCodeAsItsCanonicalForm() {
        assertThat(new BasketCurrency("pln").code()).isEqualTo("PLN");
        assertThat(new BasketCurrency(" Pln ").code()).isEqualTo("PLN");
    }

    @Test
    void refusesACodeIso4217DoesNotGiveADecimalScale() {
        assertThatThrownBy(() -> new BasketCurrency("ZZZ"))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_UNSUPPORTED));
    }
}
