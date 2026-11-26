package shop.shopping.kernel;

import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * An amount in one currency — the only representation of money in this service.
 *
 * <p>The amount is an integer in units of {@code 10^-(decimals+1)}, one digit finer than the currency's minor unit: PLN has
 * two decimals, so one fine unit is a tenth of a grosz and {@code 99,89 zł} is {@code 99890}. The extra digit lets a
 * discount be computed without losing precision, while the single rounding step stays with the basket. The scale belongs to
 * the currency — {@link #fineUnitsPer(String)} is the one place that decides it, so a non-PLN currency (D21) is a lookup and
 * not a rewrite.
 *
 * <p>Nothing is ever stored negative: a discount is a positive amount of kind {@link Kind#discount}, the only kind rendered
 * with a minus, and a subtraction that would go negative is refused (RULE-6). Addition and multiplication are exact integer
 * arithmetic, and no conversion between currencies exists — one basket holds one currency (RULE-2).
 *
 * <p>Rounding happens exactly once, at the boundary that presents or compares an amount: {@link #toMinor()} rounds half-up
 * and {@link #render()} is the {@code pl-PL} text built from it (RULE-5, RULE-7). Zero is an amount, never an absence
 * (RULE-8).
 *
 * <p>Every refusal of a value the client supplied is {@link ShoppingException} (D48): a value object may not throw a JDK
 * type, because no error advice can map one to the code its contract promises. An absent component is not such a refusal —
 * a missing {@code amount} or {@code currency} is a programming error and stays an {@link Objects#requireNonNull}
 * {@link NullPointerException}, while a missing {@code kind} is neither: a document written before the component existed
 * carries no {@code kind}, and its creator is this canonical constructor, so an absent kind reads as a {@link Kind#charge}.
 */
public record Money(long amount, String currency, Kind kind) {

    /** Whether the amount adds to what the customer pays, or is the discount reported beside it (RULE-6). */
    public enum Kind {
        charge,
        discount
    }

    private static final Locale POLISH = Locale.forLanguageTag("pl-PL");

    private static final String MINUS = "\u2212";

    public Money {
        kind = kind == null ? Kind.charge : kind;
        if (amount < 0) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_NEGATIVE, amount);
        }
        decimals(currency);
    }

    /** A charge — the kind of every amount that is not a discount. */
    public Money(long amount, String currency) {
        this(amount, currency, Kind.charge);
    }

    /** Zero in a currency — an amount the customer really sees ({@code 0,00 zł}), never "unknown" (RULE-8). */
    public static Money zero(String currency) {
        return new Money(0, currency, Kind.charge);
    }

    /**
     * Zero of a kind — the discount sum starts as {@code 0,00 zł} of kind {@code discount}, so the reported discount is the
     * same kind as every discount added to it (RULE-6).
     *
     * @param currency the currency the sum is kept in
     * @param kind     the kind of the amounts the sum will hold
     * @return the zero amount of that kind
     */
    public static Money zero(String currency, Kind kind) {
        return new Money(0, currency, kind);
    }

    /** How many fine units make one whole currency unit: {@code 10^(decimals+1)} — 1000 for PLN, 10 for JPY, 10000 for BHD. */
    public static long fineUnitsPer(String currency) {
        return pow10(decimals(currency) + 1);
    }

    /** This amount {@code quantity} times — exact, no rounding (RULE-4). */
    public Money times(int quantity) {
        if (quantity < 1) {
            throw new ShoppingException(ShoppingException.Error.QUANTITY_NOT_POSITIVE, quantity);
        }
        try {
            return new Money(Math.multiplyExact(amount, quantity), currency, kind);
        } catch (ArithmeticException beyondTheFineUnit) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_OUT_OF_RANGE,
                    beyondTheFineUnit, expression("x", quantity), currency);
        }
    }

    /** This amount plus {@code other}, exact (RULE-3) — refused across currencies and across kinds. */
    public Money plus(Money other) {
        Objects.requireNonNull(other, "other");
        ensureSameCurrency(other);
        ensureSameKind(other);
        try {
            return new Money(Math.addExact(amount, other.amount), currency, kind);
        } catch (ArithmeticException beyondTheFineUnit) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_OUT_OF_RANGE,
                    beyondTheFineUnit, expression("+", other.amount), currency);
        }
    }

    /**
     * This amount less {@code other}, keeping this amount's kind — refused when the result would be negative (RULE-6).
     *
     * <p>The kind travels from this amount, not from {@code other}. Nothing in the basket subtracts a discount from a charge:
     * element 04's {@code productValue} is already net (D22) and only reports {@code discountValue} beside it, so a subtraction
     * of that kind here would be the double count S7 exists to catch.
     */
    public Money minus(Money other) {
        Objects.requireNonNull(other, "other");
        ensureSameCurrency(other);
        long result = subtractOrRefuse(other);
        return new Money(result, currency, kind);
    }

    /** The amount in the currency's minor unit — the one place the half-up rounding step happens (RULE-5). */
    public long toMinor() {
        long minor = amount / 10;
        return amount % 10 < 5 ? minor : minor + 1;
    }

    /** The {@code pl-PL} text of the rounded amount: {@code 99,89 zł}, U+00A0 before the code, minus only for a discount. */
    public String render() {
        long minor = toMinor();
        int decimals = decimals(currency);
        long minorPer = fineUnitsPer(currency) / 10;
        String fraction = decimals == 0 ? "" : "," + pad(minor % minorPer, decimals);
        String sign = kind == Kind.discount ? MINUS : "";
        return sign + (minor / minorPer) + fraction + '\u00A0' + Currency.getInstance(currency).getSymbol(POLISH);
    }

    private long subtractOrRefuse(Money other) {
        long result;
        try {
            result = Math.subtractExact(amount, other.amount);
        } catch (ArithmeticException beyondTheFineUnit) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_OUT_OF_RANGE,
                    beyondTheFineUnit, expression("-", other.amount), currency);
        }
        if (result < 0) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_WOULD_BE_NEGATIVE,
                    other.amount, amount, currency);
        }
        return result;
    }

    /** The arithmetic that left the fine unit, as the message names it — the values, never a JDK type's text. */
    private String expression(String operator, long operand) {
        return amount + " " + operator + " " + operand;
    }

    private void ensureSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new ShoppingException(ShoppingException.Error.CURRENCY_MISMATCH,
                    other.currency, currency);
        }
    }

    private void ensureSameKind(Money other) {
        if (kind != other.kind) {
            throw new ShoppingException(ShoppingException.Error.AMOUNT_KIND_MISMATCH, other.kind, kind);
        }
    }

    /**
     * The number of decimal digits the currency has — the single currency-to-scale lookup.
     *
     * <p>It refuses a code the ISO-4217 table does not know and a pseudo-currency with no decimal digits, so the scale of an
     * amount is decided before the amount exists.
     */
    private static int decimals(String currency) {
        int decimals;
        try {
            decimals = Currency.getInstance(Objects.requireNonNull(currency, "currency")).getDefaultFractionDigits();
        } catch (IllegalArgumentException notACurrency) {
            throw new ShoppingException(ShoppingException.Error.CURRENCY_UNSUPPORTED, notACurrency, currency);
        }
        if (decimals < 0) {
            throw new ShoppingException(ShoppingException.Error.CURRENCY_UNSUPPORTED, currency);
        }
        return decimals;
    }

    private static long pow10(int exponent) {
        long power = 1;
        for (int digit = 0; digit < exponent; digit++) {
            power *= 10;
        }
        return power;
    }

    private static String pad(long value, int digits) {
        String text = Long.toString(value);
        return "0".repeat(Math.max(0, digits - text.length())) + text;
    }
}
