package shop.fulfillment.kernel;

/**
 * Where the parcel goes: a pickup point named by an opaque point id, or a street address — exactly one of the two,
 * consistent with the declared kind (element 01 RULE-7).
 */
public record DeliveryLocation(LocationKind kind, String pointId, Address address) {

    public DeliveryLocation {
        boolean byPoint = kind == LocationKind.PICKUP_POINT && pointId != null && address == null;
        boolean byAddress = kind == LocationKind.ADDRESS && address != null && pointId == null;
        if (!byPoint && !byAddress) {
            throw new FulfillmentException(
                    FulfillmentException.Error.DELIVERY_LOCATION_AMBIGUOUS, pointId, kind);
        }
    }
}
