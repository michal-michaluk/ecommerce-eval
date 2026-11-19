package shop.fulfillment.kernel;

/**
 * One parcel as the processing resource of element 11 renders it: our own reference, the identity the carrier assigned and
 * the furthest delivery leg it reported.
 *
 * <p>It is the parcel slice's answer to a question another slice asks, so it carries the three values the wire flattens onto
 * {@code parcel} and nothing of the parcel's own record — no legs list, no weight, no collection handle.
 *
 * @param parcelId        our identity of the parcel
 * @param reference       our key, never derived from a carrier number
 * @param carrierIdentity the identity the carrier assigned; the only carrier identity that exists
 * @param legState        the furthest leg the parcel reached
 */
public record ParcelState(
        ParcelId parcelId,
        ParcelReference reference,
        CarrierIdentity carrierIdentity,
        LegState legState) {

    public ParcelState {
        if (parcelId == null || reference == null || carrierIdentity == null || legState == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "parcelState");
        }
    }
}
