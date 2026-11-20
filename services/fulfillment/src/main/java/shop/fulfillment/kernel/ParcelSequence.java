package shop.fulfillment.kernel;

/**
 * The position of one parcel in the order's own packing sequence, {@code paczka i/n}, fixed at registration because it
 * is printed on the first label.
 *
 * <p>This context registers exactly one parcel per processing (register D10), so {@code ofTotal} is 1 here; the type
 * still says what the carrier will print.
 */
public record ParcelSequence(int index, int ofTotal) {

    public ParcelSequence {
        if (index < 1 || ofTotal < 1 || index > ofTotal) {
            throw new FulfillmentException(
                    FulfillmentException.Error.PARCEL_SEQUENCE_IMPOSSIBLE, index, ofTotal);
        }
    }
}
