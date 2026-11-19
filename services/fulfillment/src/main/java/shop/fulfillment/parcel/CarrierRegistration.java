package shop.fulfillment.parcel;

import lombok.Builder;

import java.util.stream.Stream;

import shop.fulfillment.kernel.Address;
import shop.fulfillment.kernel.Delivery;
import shop.fulfillment.kernel.DeliveryLocation;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.RegistrationRequest;

/**
 * What a registration needs from configuration rather than from the order: the seller's address that prints on the label,
 * the weight of one parcel, and how many parcels an order is packed into.
 *
 * <p>Nothing of it is order content. The sender is ours and appears on no order ({@code 14-carrier-port.md}); the weight's
 * source is open in the sources ({@code UND-06}, element 03 RULE-6), so the deployment states it and the default is recorded
 * in {@code docs/phase-1-decisions.md}; and {@code ofTotal} is one parcel per processing by register D10, fixed before the
 * first label is printed (element 03 RULE-3).
 *
 * @param senderStreet      the seller's street, from configuration
 * @param senderCity        the seller's city, from configuration
 * @param senderPostalCode  the seller's postal code, from configuration
 * @param senderCountry     the seller's country, from configuration
 * @param weightGrams       the weight of one parcel in grams, from configuration
 */
@Builder
record CarrierRegistration(
        String senderStreet,
        String senderCity,
        String senderPostalCode,
        String senderCountry,
        int weightGrams) {

    static final int ONE_PARCEL = 1;

    /**
     * The request one parcel of one order is registered with: our reference, the configured sender, the order's own
     * delivery target and service, the configured weight, and one parcel of one.
     *
     * <p>A deployment that configured a carrier without a sender is refused here, naming the block it forgot — never at
     * startup, so a service with no carrier still boots (element 14, spec D14).
     *
     * @param reference our key of the parcel being registered
     * @param delivery  where the order goes and which service the customer bought
     * @return the request the carrier is asked with
     */
    RegistrationRequest requestFor(ParcelReference reference, Delivery delivery) {
        DeliveryLocation location = delivery.location();
        return RegistrationRequest.builder()
                .reference(reference)
                .sender(sender())
                .receiver(location.address())
                .pickupPointId(location.pointId())
                .weightGrams(weightGrams)
                .ofTotal(ONE_PARCEL)
                .serviceCode(delivery.methodCode())
                .build();
    }

    private Address sender() {
        if (Stream.of(senderStreet, senderCity, senderPostalCode, senderCountry).anyMatch(String::isBlank)) {
            throw new FulfillmentException(
                    FulfillmentException.Error.COMMAND_VALUE_MISSING, "fulfillment.carrier.sender");
        }
        return new Address(senderStreet, senderCity, senderPostalCode, senderCountry);
    }
}
