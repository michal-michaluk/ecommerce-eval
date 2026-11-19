package shop.fulfillment.processing;

/**
 * The carrier's printable document: its bytes and the format they were rendered in.
 *
 * <p>We never author a carrier-owned field and never read data back out of these bytes — a printed label is a document,
 * not a data source.
 */
public record LabelDocument(byte[] bytes, String format) {

    public LabelDocument {
        if (bytes == null || bytes.length == 0) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "labelBytes");
        }
        if (format == null || format.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "labelFormat");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
