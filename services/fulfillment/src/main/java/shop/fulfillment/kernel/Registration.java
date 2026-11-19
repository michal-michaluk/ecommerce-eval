package shop.fulfillment.kernel;

/**
 * The carrier's answer to a registration.
 *
 * <p>It carries the identity the carrier assigned, our reference it was asked with, and the sequence the carrier will
 * print on the label — {@code ofTotal} is fixed at registration because the first label of a split order otherwise says
 * {@code 1/1} and is wrong.
 *
 * @param reference       our key, unchanged
 * @param carrierIdentity the identity the carrier assigned
 * @param sequence        the position of this parcel in the order's packing sequence
 */
public record Registration(ParcelReference reference, CarrierIdentity carrierIdentity, ParcelSequence sequence) {

    public Registration {
        if (reference == null || carrierIdentity == null || sequence == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "registration");
        }
    }
}
