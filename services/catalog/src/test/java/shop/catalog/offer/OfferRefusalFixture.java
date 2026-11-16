package shop.catalog.offer;

/**
 * Real offer refusals for {@code shop.catalog.ErrorMappingTest}: {@link OfferException} is package-private, so the one advice that
 * maps every context's refusal to a status can only be driven from inside this package.
 */
public final class OfferRefusalFixture {

    private OfferRefusalFixture() {
    }

    public static RuntimeException availabilityStartMissing() {
        return new OfferException(OfferException.Error.AVAILABILITY_START_MISSING, "OFR-1A2B3C4D");
    }

    public static RuntimeException offerUnknown() {
        return new OfferException(OfferException.Error.OFFER_UNKNOWN, "OFR-1A2B3C4D");
    }
}
