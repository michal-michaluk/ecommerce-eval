package shop.fulfillment.kernel;

import java.time.Instant;
import java.util.List;

/**
 * The orders the tests work with, built once here: the handover of element 12 as a value, with one line and with two, and
 * the components a refusal is proven on.
 */
public class OrderFixture {

    public static final String ORDER_HASH = "sha256:1f3c9d0a";
    public static final String PRODUCT_NUMBER = "75423";
    public static final String PRODUCT_NAME = "LEGO X-Wing 75423";
    public static final Instant PLACED_AT = Instant.parse("2026-11-19T09:31:12Z");

    public static OrderLine aLine() {
        return new OrderLine(PRODUCT_NUMBER, PRODUCT_NAME, 1);
    }

    public static OrderLine aLine(String productNumber, String name, int quantity) {
        return new OrderLine(productNumber, name, quantity);
    }

    public static Address anAddress() {
        return anAddressOf("ul. Mineralna 5A", "Kraków", "30-001", "PL");
    }

    public static Address anAddressOf(String street, String city, String postalCode, String country) {
        return new Address(street, city, postalCode, country);
    }

    public static DeliveryLocation aPickupPoint() {
        return aLocationOf(LocationKind.PICKUP_POINT, "KRA01M", null);
    }

    public static DeliveryLocation aLocationOf(LocationKind kind, String pointId, Address address) {
        return new DeliveryLocation(kind, pointId, address);
    }

    public static DeliveryLocation aStreetAddress() {
        return new DeliveryLocation(LocationKind.ADDRESS, null, anAddress());
    }

    public static Delivery aDelivery() {
        return new Delivery("PARCEL_LOCKER", aPickupPoint());
    }

    public static Payment aPayment() {
        return new Payment("PAY_DEFERRED");
    }

    public static Invoicing anInvoicing() {
        return anInvoicingOf("Jan Kowalski", null, anAddress());
    }

    public static Invoicing anInvoicingOf(String buyerName, String nip, Address address) {
        return new Invoicing(buyerName, nip, address);
    }

    public static Order anOrder() {
        return anOrder(ORDER_HASH);
    }

    public static Order anOrder(String orderHash) {
        return Order.builder()
                .orderHash(orderHash)
                .placedAt(PLACED_AT)
                .lines(List.of(aLine()))
                .delivery(aDelivery())
                .payment(aPayment())
                .invoicing(anInvoicing())
                .build();
    }

    public static Order orderWithQuantity(int quantity) {
        return Order.builder()
                .orderHash(ORDER_HASH)
                .placedAt(PLACED_AT)
                .lines(List.of(aLine(PRODUCT_NUMBER, PRODUCT_NAME, quantity)))
                .delivery(aDelivery())
                .payment(aPayment())
                .invoicing(anInvoicing())
                .build();
    }

    public static Order twoLineOrder() {
        return Order.builder()
                .orderHash(ORDER_HASH)
                .placedAt(PLACED_AT)
                .lines(List.of(aLine(PRODUCT_NUMBER, PRODUCT_NAME, 1),
                        aLine("99881", "LEGO Orchidea 99881", 3)))
                .delivery(aDelivery())
                .payment(aPayment())
                .invoicing(anInvoicing())
                .build();
    }

    public static Order orderInListingOrder() {
        return Order.builder()
                .orderHash(ORDER_HASH)
                .placedAt(PLACED_AT)
                .lines(List.of(aLine("99881", "LEGO Orchidea 99881", 3),
                        aLine(PRODUCT_NUMBER, PRODUCT_NAME, 1)))
                .delivery(aDelivery())
                .payment(aPayment())
                .invoicing(anInvoicing())
                .build();
    }
}
