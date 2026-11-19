package shop.fulfillment.kernel;

/**
 * The request to collect exactly one parcel, named by our reference and by the carrier's identity.
 *
 * <p>It is per parcel, never per multi-parcel handover, and the pickup address is configuration, so it is not request
 * data. A carrier that offers no way to change or cancel a booking gets none invented here.
 *
 * @param reference       the parcel by our key
 * @param carrierIdentity the parcel by the carrier's identity
 */
public record CollectionRequest(ParcelReference reference, CarrierIdentity carrierIdentity) {

    public CollectionRequest {
        if (reference == null || carrierIdentity == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "collectionRequest");
        }
    }
}
