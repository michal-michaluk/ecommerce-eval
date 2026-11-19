package shop.fulfillment.processing;

/**
 * A request to render the label of one parcel from stored data — a render, never a registration: it consumes no
 * number, changes no parcel and re-registers nothing.
 *
 * @param reference       the parcel by our key
 * @param carrierIdentity the parcel by the carrier's identity
 * @param format          the document format the caller wants back
 * @param layout          the layout choice the caller wants
 */
public record LabelRequest(
        ParcelReference reference,
        CarrierIdentity carrierIdentity,
        String format,
        String layout) {

    public LabelRequest {
        if (reference == null || carrierIdentity == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "labelRequest");
        }
        if (format == null || format.isBlank() || layout == null || layout.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "labelFormat");
        }
    }
}
