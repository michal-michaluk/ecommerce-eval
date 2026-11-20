package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The carrier vocabulary of elements 03 and 14: our reference and the carrier's identity never merge, a registration answers
 * the sequence it will print, and an unmapped carrier status is a value rather than an error.
 */
class CarrierValuesTest {

    @Test
    void acceptsACarrierIdentityAndRefusesABlankOne() {
        assertThat(CarrierFixture.IDENTITY.value()).isEqualTo("0234567890123");
        assertThatThrownBy(() -> new CarrierIdentity(" "))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.CARRIER_IDENTITY_MALFORMED));
    }

    @Test
    void refusesAMissingCollectionHandle() {
        assertThatThrownBy(() -> new CollectionHandle(null)).hasMessageContaining("collectionHandle");
    }

    @Test
    void aRegistrationCarriesOurReferenceTheCarrierIdentityAndTheSequence() {
        Registration registration = CarrierFixture.aRegistration();

        assertThat(registration.reference()).isEqualTo(CarrierFixture.REFERENCE);
        assertThat(registration.carrierIdentity()).isEqualTo(CarrierFixture.IDENTITY);
        assertThat(registration.sequence()).isEqualTo(new ParcelSequence(1, 1));
    }

    @Test
    void refusesARegistrationWithoutItsParts() {
        assertThatThrownBy(() -> CarrierFixture.aRegistrationOf(null, CarrierFixture.IDENTITY, new ParcelSequence(1, 1)))
                .isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> CarrierFixture.aRegistrationOf(CarrierFixture.REFERENCE, null, new ParcelSequence(1, 1)))
                .isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> CarrierFixture.aRegistrationOf(CarrierFixture.REFERENCE, CarrierFixture.IDENTITY, null))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void acceptsARequestThatNamesAPickupPoint() {
        assertThat(CarrierFixture.aRegistrationRequest().pickupPointId()).isEqualTo("KRA01M");
        assertThat(CarrierFixture.aRegistrationRequest().receiver()).isNull();
    }

    @Test
    void refusesARequestWithoutAReferenceOrSender() {
        assertThatThrownBy(() -> RegistrationRequest.builder().sender(OrderFixture.anAddress()).build())
                .isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> RegistrationRequest.builder().reference(CarrierFixture.REFERENCE).build())
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesARequestThatNamesBothDeliveryTargets() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .reference(CarrierFixture.REFERENCE)
                .sender(OrderFixture.anAddress())
                .receiver(OrderFixture.anAddress())
                .pickupPointId("KRA01M")
                .weightGrams(1200)
                .serviceCode("DPD_CLASSIC")
                .build())
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesARequestWithoutAWeightOrAService() {
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .reference(CarrierFixture.REFERENCE)
                .sender(OrderFixture.anAddress())
                .pickupPointId("KRA01M")
                .serviceCode("DPD_CLASSIC")
                .build())
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WEIGHT_NOT_POSITIVE));
        assertThatThrownBy(() -> RegistrationRequest.builder()
                .reference(CarrierFixture.REFERENCE)
                .sender(OrderFixture.anAddress())
                .pickupPointId("KRA01M")
                .weightGrams(1200)
                .build())
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void aLabelDocumentHandsOutACopyOfItsBytes() {
        LabelDocument document = CarrierFixture.aLabelDocument();
        byte[] bytes = document.bytes();
        bytes[0] = 'X';

        assertThat(document.bytes()).isNotEqualTo(bytes);
        assertThat(document.format()).isEqualTo("PDF");
    }

    @Test
    void refusesALabelDocumentWithoutBytesOrFormat() {
        assertThatThrownBy(() -> new LabelDocument(new byte[0], "PDF")).isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> new LabelDocument(new byte[]{1}, " ")).isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesALabelRequestWithoutItsParcelOrFormat() {
        assertThatThrownBy(() -> CarrierFixture.aLabelRequestOf(null, CarrierFixture.IDENTITY, "PDF", "A4"))
                .isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> CarrierFixture.aLabelRequestOf(CarrierFixture.REFERENCE, CarrierFixture.IDENTITY, " ", "A4"))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void refusesACollectionRequestWithoutItsParcel() {
        assertThatThrownBy(() -> new CollectionRequest(CarrierFixture.REFERENCE, null))
                .isInstanceOf(FulfillmentException.class);
    }

    @Test
    void anUnmappedObservationCarriesThePayloadAndNoLeg() {
        LegObservation observation = LegObservation.unmapped(
                CarrierFixture.REACHED_AT, "UNKNOWN", "{\"status\":\"UNKNOWN\"}");

        assertThat(observation.leg()).isNull();
        assertThat(observation.rawPayload()).contains("UNKNOWN");
    }

    @Test
    void refusesAnObservationWithoutItsTimeOrStatus() {
        assertThatThrownBy(() -> CarrierFixture.aLegObservationOf(Leg.L3, null, "L3", "{}"))
                .isInstanceOf(FulfillmentException.class);
        assertThatThrownBy(() -> CarrierFixture.aLegObservationOf(Leg.L3, CarrierFixture.REACHED_AT, " ", "{}"))
                .isInstanceOf(FulfillmentException.class);
    }
}
