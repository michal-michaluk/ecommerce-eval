package shop.fulfillment.processing;

import java.util.List;

/**
 * A postal address: buyer invoicing address on the order, sender or pickup address in carrier configuration.
 */
record Address(String street, String city, String postalCode, String country) {

    Address {
        requirePresent(street, "street");
        requirePresent(city, "city");
        requirePresent(postalCode, "postalCode");
        requirePresent(country, "country");
    }

    private static void requirePresent(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("address." + name));
        }
    }
}
