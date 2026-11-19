package shop.fulfillment.processing;

import lombok.Builder;

/**
 * Everything a carrier needs to register one parcel before it exists.
 *
 * <p>Sender and pickup address are configuration, not order content — the seller party is ours and appears on no order.
 * The receiver is either a street address or a pickup point, never both: the point is an opaque value each carrier
 * carries in its own field, and nobody owns its validation.
 *
 * <p>The cash-on-delivery and declared-value figures are opaque pass-through values owned by their source: nothing here
 * stores, compares, converts or formats them.
 *
 * @param reference      our key, unique and never derived from a carrier number
 * @param sender         the seller's address, from configuration
 * @param receiver       the street address the parcel goes to, or {@code null} for a pickup point
 * @param pickupPointId  the pickup point the parcel goes to, or {@code null} for a street address
 * @param weightGrams    the weight of this parcel in grams, never of the order
 * @param ofTotal        how many parcels the order is packed into, known before the first label is printed
 * @param serviceCode    the carrier service the caller selected
 * @param cashOnDelivery the opaque cash-on-delivery value, or {@code null} when the order carries none
 * @param declaredValue  the opaque declared-value figure, or {@code null} when the order carries none
 */
@Builder
public record RegistrationRequest(
        ParcelReference reference,
        Address sender,
        Address receiver,
        String pickupPointId,
        int weightGrams,
        int ofTotal,
        String serviceCode,
        String cashOnDelivery,
        String declaredValue) {

    public RegistrationRequest {
        if (reference == null || sender == null) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "registrationRequest");
        }
        ensureDeliveryTargetExists(receiver, pickupPointId);
        if (weightGrams < 1) {
            throw new FulfillmentException(FulfillmentException.Error.WEIGHT_NOT_POSITIVE, weightGrams);
        }
        if (serviceCode == null || serviceCode.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "serviceCode");
        }
    }

    private static void ensureDeliveryTargetExists(Address receiver, String pickupPointId) {
        boolean byAddress = receiver != null && pickupPointId == null;
        boolean byPoint = receiver == null && pickupPointId != null;
        if (!byAddress && !byPoint) {
            throw new FulfillmentException(
                    FulfillmentException.Error.DELIVERY_LOCATION_AMBIGUOUS, pickupPointId, "registration");
        }
    }
}
