package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The components of element 01: exact one location kind, an address that has all four parts, and the two opaque method
 * codes this context never enumerates.
 */
class OrderComponentsTest {

    @Test
    void acceptsAPickupPointAsTheDeliveryLocation() {
        assertThat(OrderFixture.aPickupPoint().pointId()).isEqualTo("KRA01M");
        assertThat(OrderFixture.aPickupPoint().address()).isNull();
    }

    @Test
    void acceptsAStreetAddressAsTheDeliveryLocation() {
        assertThat(OrderFixture.aStreetAddress().address()).isEqualTo(OrderFixture.anAddress());
    }

    @Test
    void refusesAPickupPointThatAlsoCarriesAnAddress() {
        assertThatThrownBy(() -> OrderFixture.aLocationOf(LocationKind.PICKUP_POINT, "KRA01M", OrderFixture.anAddress()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ORDER_INCOMPLETE);
                    assertThat(refused.details()).containsExactly(List.of("delivery.location.pointId"));
                });
    }

    @Test
    void refusesAStreetAddressWithoutAnAddress() {
        assertThatThrownBy(() -> OrderFixture.aLocationOf(LocationKind.ADDRESS, null, null))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.details())
                        .containsExactly(List.of("delivery.location.address")));
    }

    @Test
    void refusesAStreetAddressThatCarriesAPointIdToo() {
        assertThatThrownBy(() -> OrderFixture.aLocationOf(LocationKind.ADDRESS, "KRA01M", OrderFixture.anAddress()))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesADeliveryWithoutAMethodCode() {
        assertThatThrownBy(() -> new Delivery(" ", OrderFixture.aPickupPoint()))
                .hasMessageContaining("delivery.methodCode");
    }

    @Test
    void refusesADeliveryWithoutALocation() {
        assertThatThrownBy(() -> new Delivery("PARCEL_LOCKER", null))
                .hasMessageContaining("delivery.location");
    }

    @Test
    void refusesAnAddressWithAMissingPart() {
        assertThatThrownBy(() -> OrderFixture.anAddressOf("ul. Mineralna 5A", " ", "30-001", "PL"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.details())
                        .containsExactly(List.of("address.city")));
    }

    @Test
    void refusesAPaymentWithoutAMethodCode() {
        assertThatThrownBy(() -> new Payment(null)).hasMessageContaining("payment.methodCode");
    }

    @Test
    void refusesInvoicingWithoutTheBuyerName() {
        assertThatThrownBy(() -> OrderFixture.anInvoicingOf(null, null, OrderFixture.anAddress()))
                .hasMessageContaining("invoicing.buyerName");
    }

    @Test
    void refusesInvoicingWithAnEmptyTaxId() {
        assertThatThrownBy(() -> OrderFixture.anInvoicingOf("Jan Kowalski", " ", OrderFixture.anAddress()))
                .hasMessageContaining("invoicing.nip");
    }

    @Test
    void refusesInvoicingWithoutAnAddress() {
        assertThatThrownBy(() -> OrderFixture.anInvoicingOf("Jan Kowalski", null, null))
                .hasMessageContaining("invoicing.address");
    }

    @Test
    void acceptsInvoicingWithoutATaxIdBecauseAConsumerCarriesNone() {
        assertThat(OrderFixture.anInvoicing().nip()).isNull();
    }

    @Test
    void refusesALineWithoutAProductNumber() {
        assertThatThrownBy(() -> OrderFixture.aLine(null, "name", 1))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ORDER_INCOMPLETE);
                    assertThat(refused.details()).containsExactly(List.of("lines.productNumber"));
                });
    }

    @Test
    void refusesALineWithoutAName() {
        assertThatThrownBy(() -> OrderFixture.aLine("75423", " ", 1)).hasMessageContaining("lines.name");
    }

    @Test
    void refusesALineWithANonPositiveQuantity() {
        assertThatThrownBy(() -> OrderFixture.aLine("75423", "name", 0))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ORDER_INCOMPLETE);
                    assertThat(refused.details()).containsExactly(List.of("lines.quantity"));
                });
    }
}
