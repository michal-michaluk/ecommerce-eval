package shop.fulfillment.kernel;

import java.util.List;

/**
 * Where the parcel goes: a pickup point named by an opaque point id, or a street address — exactly one of the two,
 * consistent with the declared kind (element 01 RULE-7).
 *
 * <p>A location that names neither or both refuses the handover as {@code ORDER_INCOMPLETE} and names the one part the
 * payload has to carry — {@code delivery.location.pointId} or {@code delivery.location.address}, the path element 11 spells
 * out in its {@code 422} row.
 */
public record DeliveryLocation(LocationKind kind, String pointId, Address address) {

    public DeliveryLocation {
        boolean byPoint = kind == LocationKind.PICKUP_POINT && pointId != null && address == null;
        boolean byAddress = kind == LocationKind.ADDRESS && address != null && pointId == null;
        if (!byPoint && !byAddress) {
            throw new FulfillmentException(
                    FulfillmentException.Error.ORDER_INCOMPLETE, List.of(absentPart(kind)));
        }
    }

    private static String absentPart(LocationKind kind) {
        return kind == LocationKind.PICKUP_POINT
                ? "delivery.location.pointId"
                : "delivery.location.address";
    }
}
