package shop.fulfillment.processing;

/**
 * The identity a carrier assigns to one parcel — a waybill, a tracking number, a {@code numerNadania}, a shipment id,
 * whichever the carrier calls it.
 *
 * <p>It is only ever assigned from a response: never invented, never parsed off paper, and never merged with our own
 * {@link ParcelReference}.
 */
public record CarrierIdentity(String value) {

    public CarrierIdentity {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.CARRIER_IDENTITY_MALFORMED, value);
        }
    }
}
