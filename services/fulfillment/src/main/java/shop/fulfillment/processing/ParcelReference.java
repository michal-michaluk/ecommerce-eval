package shop.fulfillment.processing;

/**
 * Our own key for one parcel — unique, at most 50 characters, and the only correlation key a registration, a label and
 * a collection request are addressed by.
 *
 * <p>It is never derived from a carrier number, and a printed label never gives it back: the label artifact carries no
 * order reference and no barcode value, so correlation exists only in the carrier's response.
 */
public record ParcelReference(String value) {

    private static final int MAX_LENGTH = 50;

    public ParcelReference {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw new FulfillmentException(FulfillmentException.Error.PARCEL_REFERENCE_MALFORMED, value);
        }
    }
}
