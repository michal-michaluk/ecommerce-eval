package shop.catalog.pricing;

/**
 * A real pricing refusal for {@code shop.catalog.ErrorMappingTest}: {@link PricingException} is package-private, so the one advice that
 * maps every context's refusal to a status can only be driven from inside this package.
 */
public final class PricingRefusalFixture {

    private PricingRefusalFixture() {
    }

    public static RuntimeException discountCurrencyNotPriced() {
        return new PricingException(PricingException.Error.DISCOUNT_CURRENCY_NOT_PRICED, "DSC-1A2B3C4D");
    }
}
