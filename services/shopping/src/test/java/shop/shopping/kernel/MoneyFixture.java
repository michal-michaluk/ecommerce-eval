package shop.shopping.kernel;

/**
 * The amounts element 03 names: the two offer prices of the basket screen, the 10,00 zł catalog discount and one foreign
 * price, all in fine units.
 */
public class MoneyFixture {

    public static final String PLN = "PLN";

    private MoneyFixture() {
    }

    public static Money anAmount(long amount) {
        return new Money(amount, PLN);
    }

    public static Money anAmountIn(long amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money aCurrentPrice() {
        return anAmount(99890);
    }

    public static Money aSecondPrice() {
        return anAmount(64990);
    }

    public static Money anEuroPrice() {
        return anAmountIn(99890, "EUR");
    }

    public static Money aDiscount() {
        return new Money(10000, PLN, Money.Kind.discount);
    }

    public static Money anAmountWithoutAKind() {
        return new Money(99890, PLN, null);
    }
}
