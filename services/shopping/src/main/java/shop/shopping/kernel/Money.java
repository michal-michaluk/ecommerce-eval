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
 * the currency — {@link #fineUnitsPer(String)} is the one place that decides it, so the first non-PLN currency (D21) is a
 * lookup and not a rewrite.
 *
 * <p>Nothing is ever stored negative: a discount is a positive amount of kind {@link Kind#discount}, the only kind rendered
 * with a minus, and a subtraction that would go negative is refused (RULE-6). Addition and multiplication are exact integer
 * arithmetic, and no conversion between currencies exists — one basket holds one currency (RULE-2).
 *
 * <p>Rounding happens exactly once, at the boundary that presents or compares an amount: {@link #toMinor()} rounds half-up
 * and {@link #render()} is the {@code pl-PL} text built from it (RULE-5, RULE-7). Zero is an amount, never an absence
 * (RULE-8).
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
        Objects.requireNonNull(kind, "kind");
        if (amount < 0) {
            throw new IllegalArgumentException(
                    "amount " + amount + " is negative, a discount is a positive amount of kind discount");
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

    /** How many fine units make one whole currency unit: {@code 10^(decimals+1)} — 1000 for PLN, 10 for JPY, 10000 for BHD. */
    public static long fineUnitsPer(String currency) {
        return pow10(decimals(currency) + 1);
    }

    /** This amount {@code quantity} times — exact, no rounding (RULE-4). */
    public Money times(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, got " + quantity);
        }
        return new Money(Math.multiplyExact(amount, quantity), currency, kind);
    }

    /** This amount plus {@code other}, exact (RULE-3) — refused across currencies and across kinds. */
    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot add " + other.currency + " to " + currency + ", no conversion between currencies exists");
        }
        if (kind != other.kind) {
            throw new IllegalArgumentException("cannot add a " + other.kind + " to a " + kind + ", a discount is subtracted");
        }
        return new Money(Math.addExact(amount, other.amount), currency, kind);
    }

    /**
     * This amount less {@code other}, keeping this amount's kind — refused when the result would be negative (RULE-6).
     *
     * <p>The kind travels from this amount, not from {@code other}. Nothing in the basket subtracts a discount from a charge:
     * element 04's {@code productValue} is already net (D22) and only reports {@code discountValue} beside it, so a subtraction
     * of that kind here would be the double count S7 exists to catch.
     */
    public Money minus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot subtract " + other.currency + " from " + currency + ", no conversion between currencies exists");
        }
        long result = Math.subtractExact(amount, other.amount);
        if (result < 0) {
            throw new IllegalArgumentException(
                    "subtracting " + other.amount + " from " + amount + " " + currency + " is negative, an amount never is");
        }
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

    /**
     * The number of decimal digits the currency has — the single currency-to-scale lookup.
     *
     * <p>It refuses a code the ISO-4217 table does not know and a pseudo-currency with no decimal digits, so the scale of an
     * amount is decided before the amount exists.
     */
    private static int decimals(String currency) {
        int decimals = Currency.getInstance(Objects.requireNonNull(currency, "currency")).getDefaultFractionDigits();
        if (decimals < 0) {
            throw new IllegalArgumentException(currency + " has no decimal digits, it cannot carry an amount");
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
