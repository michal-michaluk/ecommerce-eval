package shop.shopping.kernel;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 03: exact integer money in one currency, one explicit half-up rounding step and {@code pl-PL} rendering. Every
 * refusal is the service's own {@link ShoppingException} (D48), so the error advice can map it to the code the contract
 * promises instead of to a {@code 500}.
 */
class MoneyTest {

    @Test
    void addsTwoPricesExactly() {
        Money sum = MoneyFixture.aCurrentPrice().plus(MoneyFixture.aSecondPrice());

        assertThat(sum.amount()).isEqualTo(164880);
        assertThat(sum.currency()).isEqualTo(MoneyFixture.PLN);
    }

    @Test
    void multipliesByQuantityExactly() {
        Money lineTotal = MoneyFixture.aCurrentPrice().times(2);

        assertThat(lineTotal.amount()).isEqualTo(199780);
    }

    @Test
    void roundsHalfUpExactlyOnceAtTheBoundary() {
        assertThat(MoneyFixture.anAmount(164880).toMinor()).isEqualTo(16488);
        assertThat(MoneyFixture.anAmount(33333).toMinor()).isEqualTo(3333);
        assertThat(MoneyFixture.anAmount(15).toMinor()).isEqualTo(2);
        assertThat(MoneyFixture.anAmount(14).toMinor()).isEqualTo(1);
    }

    @Test
    void roundsOnceAfterComputingInTheFineUnit() {
        Money sum = MoneyFixture.anAmount(33333).plus(MoneyFixture.anAmount(33333));

        assertThat(sum.amount()).isEqualTo(66666);
        assertThat(sum.toMinor()).isEqualTo(6667);
        assertThat(MoneyFixture.anAmount(99895).render()).isEqualTo("99,90\u00A0zł");
    }

    @Test
    void roundsIntoTheWholeUnitWithoutLosingTheScale() {
        assertThat(MoneyFixture.anAmount(99995).toMinor()).isEqualTo(10000);
        assertThat(MoneyFixture.anAmount(99995).render()).isEqualTo("100,00\u00A0zł");
    }

    @Test
    void roundsAndRendersWithTheScaleOfAForeignCurrency() {
        assertThat(MoneyFixture.anAmountIn(12349, "BHD").toMinor()).isEqualTo(1235);
        assertThat(MoneyFixture.anAmountIn(1000, "JPY").toMinor()).isEqualTo(100);
        assertThat(MoneyFixture.anAmountIn(1000, "JPY").render()).isEqualTo("100\u00A0JPY");
        assertThat(MoneyFixture.anAmountIn(10000, "BHD").render()).isEqualTo("1,000\u00A0BHD");
    }

    @Test
    void rendersPolishMoneyWithACommaAndANonBreakingSpace() {
        String rendered = MoneyFixture.aCurrentPrice().render();

        assertThat(rendered).isEqualTo("99,89\u00A0zł");
        assertThat(rendered.charAt(5)).isEqualTo('\u00A0');
        assertThat(MoneyFixture.aCurrentPrice().amount()).isEqualTo(99890);
    }

    @Test
    void rendersTheSameTextWhateverTheDefaultLocaleIs() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(MoneyFixture.aCurrentPrice().render()).isEqualTo("99,89\u00A0zł");
            assertThat(MoneyFixture.anAmountIn(10000, "BHD").render()).isEqualTo("1,000\u00A0BHD");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void zeroIsAValidAmount() {
        Money zero = Money.zero(MoneyFixture.PLN);

        assertThat(zero.amount()).isZero();
        assertThat(zero.kind()).isEqualTo(Money.Kind.charge);
        assertThat(zero.render()).isEqualTo("0,00\u00A0zł");
    }

    @Test
    void zeroOfTheDiscountKindIsTheStartOfADiscountSum() {
        Money nothingDiscounted = Money.zero(MoneyFixture.PLN, Money.Kind.discount);

        assertThat(nothingDiscounted.amount()).isZero();
        assertThat(nothingDiscounted.kind()).isEqualTo(Money.Kind.discount);
        assertThat(nothingDiscounted.plus(MoneyFixture.aDiscount())).isEqualTo(MoneyFixture.aDiscount());
    }

    @Test
    void keepsTheKindThroughMultiplicationAndSubtraction() {
        assertThat(MoneyFixture.aDiscount().times(2).kind()).isEqualTo(Money.Kind.discount);
        assertThat(MoneyFixture.aDiscount().minus(MoneyFixture.aDiscount()).kind()).isEqualTo(Money.Kind.discount);
        assertThat(MoneyFixture.aCurrentPrice().times(2).kind()).isEqualTo(Money.Kind.charge);
    }

    @Test
    void neverEditsAnAmountAChangeProducesANewOne() {
        Money price = MoneyFixture.aCurrentPrice();

        Money doubled = price.times(2);

        assertThat(doubled).isNotSameAs(price);
        assertThat(price).isEqualTo(MoneyFixture.aCurrentPrice());
    }

    @Test
    void subtractsToZeroAndZeroStaysAnAmount() {
        Money nothing = MoneyFixture.aCurrentPrice().minus(MoneyFixture.aCurrentPrice());

        assertThat(nothing.amount()).isZero();
        assertThat(nothing.render()).isEqualTo(Money.zero(MoneyFixture.PLN).render());
    }

    @Test
    void storesADiscountAsAPositiveAmountOfKindDiscount() {
        Money discount = MoneyFixture.aDiscount();

        assertThat(discount.amount()).isEqualTo(10000);
        assertThat(discount.kind()).isEqualTo(Money.Kind.discount);
        assertThat(discount.render()).isEqualTo("\u221210,00\u00A0zł");
    }

    @Test
    void subtractsADiscountFromAChargeAndKeepsTheCharge() {
        Money total = MoneyFixture.aCurrentPrice().minus(MoneyFixture.aDiscount());

        assertThat(total.amount()).isEqualTo(89890);
        assertThat(total.kind()).isEqualTo(Money.Kind.charge);
    }

    @Test
    void takesTheScaleFromTheCurrencyNeverFromAConstant() {
        assertThat(Money.fineUnitsPer(MoneyFixture.PLN)).isEqualTo(1000);
        assertThat(Money.fineUnitsPer("JPY")).isEqualTo(10);
        assertThat(Money.fineUnitsPer("BHD")).isEqualTo(10000);
    }

    @Test
    void readsAnAbsentKindAsAChargeAndRefusesAnAbsentCurrency() {
        assertThat(MoneyFixture.anAmountWithoutAKind()).isEqualTo(MoneyFixture.aCurrentPrice());
        assertThatThrownBy(() -> Money.zero(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesANegativeAmount() {
        assertThatThrownBy(() -> MoneyFixture.anAmount(-1))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.AMOUNT_NEGATIVE));
    }

    @Test
    void refusesASubtractionThatWouldGoNegative() {
        assertThatThrownBy(() -> MoneyFixture.aSecondPrice().minus(MoneyFixture.aCurrentPrice()))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.AMOUNT_WOULD_BE_NEGATIVE));
    }

    @Test
    void refusesACurrencyWithoutADecimalScale() {
        assertThatThrownBy(() -> Money.zero("ZZZ"))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_UNSUPPORTED));
        assertThatThrownBy(() -> Money.zero("XAU"))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_UNSUPPORTED));
    }

    @Test
    void refusesASumAcrossCurrenciesAndAcrossKinds() {
        assertThatThrownBy(() -> MoneyFixture.aCurrentPrice().plus(MoneyFixture.anEuroPrice()))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
        assertThatThrownBy(() -> MoneyFixture.aCurrentPrice().plus(MoneyFixture.aDiscount()))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.AMOUNT_KIND_MISMATCH));
        assertThatThrownBy(() -> MoneyFixture.aCurrentPrice().minus(MoneyFixture.anEuroPrice()))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.CURRENCY_MISMATCH));
    }

    @Test
    void refusesAQuantityBelowOne() {
        assertThatThrownBy(() -> MoneyFixture.aCurrentPrice().times(0))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.QUANTITY_NOT_POSITIVE));
    }

    @Test
    void refusesAnArithmeticThatWouldLeaveTheFineUnit() {
        assertThatThrownBy(() -> MoneyFixture.anAmount(Long.MAX_VALUE).times(2))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.AMOUNT_OUT_OF_RANGE));
        assertThatThrownBy(() -> MoneyFixture.anAmount(Long.MAX_VALUE).plus(MoneyFixture.anAmount(1)))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.AMOUNT_OUT_OF_RANGE));
    }

    @Test
    void rendersARefusalWithItsOwnValuesAndNoJdkText() {
        assertThatThrownBy(() -> MoneyFixture.anAmount(Long.MAX_VALUE).times(2))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.AMOUNT_OUT_OF_RANGE);
                    assertThat(refused.getMessage()).contains("9223372036854775807 x 2").contains("PLN");
                    assertThat(refused.details()).containsExactly("9223372036854775807 x 2", "PLN");
                    assertThat(refused.getMessage()).doesNotContain("java.lang.");
                    assertThat(refused.getCause()).isInstanceOf(ArithmeticException.class);
                });
    }

    @Test
    void roundsTheLargestFineAmountWithoutLeavingIt() {
        assertThat(MoneyFixture.anAmount(Long.MAX_VALUE).toMinor()).isEqualTo(922337203685477581L);
    }
}
