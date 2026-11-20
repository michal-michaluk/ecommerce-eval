package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.LegReached;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.ParcelSequence;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.Registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 03: a parcel exists only once a carrier answered a registration, our reference and the carrier's identity never
 * merge, legs move forward only, and an unmapped status is stored without moving anything.
 */
class ParcelEditorTest {

    static final ParcelId PARCEL_ID = ProcessingFixture.PARCEL_ID;

    @Test
    void opensTheParcelFromTheCarriersAnswerAndRecordsTheFirstLeg() {
        ParcelEditor parcel = registered();

        Parcel snapshot = parcel.toSnapshot();
        assertThat(snapshot.carrierIdentity()).isEqualTo(CarrierFixture.IDENTITY);
        assertThat(snapshot.reference()).isEqualTo(CarrierFixture.REFERENCE);
        assertThat(snapshot.legs()).containsExactly(new LegReached(Leg.L1, CarrierFixture.REACHED_AT));
        assertThat(snapshot.legState()).isEqualTo(LegState.PARCEL_REGISTERED);
        assertThat(parcel.events).singleElement()
                .isInstanceOf(DomainEvent.DeliveryNumberGenerated.class);
    }

    @Test
    void refusesMoreThanOneParcelForOneProcessing() {
        Registration split = CarrierFixture.aRegistrationOf(
                CarrierFixture.REFERENCE, CarrierFixture.IDENTITY, new ParcelSequence(1, 2));

        assertThatThrownBy(() -> ParcelEditor.registerFor(PARCEL_ID, ProcessingFixture.PROCESSING_ID,
                CarrierFixture.aRegistrationRequest(), split, CarrierFixture.REACHED_AT))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PARCEL_SPLIT_UNSUPPORTED));
    }

    @Test
    void refusesAnAnswerForAnotherReference() {
        Registration otherReference = CarrierFixture.aRegistrationOf(
                new ParcelReference("PCL-REF-9999"), CarrierFixture.IDENTITY, new ParcelSequence(1, 1));

        assertThatThrownBy(() -> ParcelEditor.registerFor(PARCEL_ID, ProcessingFixture.PROCESSING_ID,
                CarrierFixture.aRegistrationRequest(), otherReference, CarrierFixture.REACHED_AT))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PARCEL_REFERENCE_MISMATCH));
    }

    @Test
    void asksForOneCollectionAndNeverForASecond() {
        ParcelEditor parcel = registered();

        parcel.markReady(new CollectionHandle("COL-1"));
        parcel.markReady(new CollectionHandle("COL-2"));

        assertThat(parcel.toSnapshot().collectionHandle()).isEqualTo(new CollectionHandle("COL-1"));
        assertThat(parcel.toSnapshot().legState()).isEqualTo(LegState.PARCEL_READY);
        assertThat(parcel.events).hasSize(2);
    }

    @Test
    void movesForwardOnANewLeg() {
        ParcelEditor parcel = registered();

        parcel.applyNotification(CarrierFixture.aLegObservation(Leg.L3));

        assertThat(parcel.toSnapshot().legState()).isEqualTo(LegState.ASSIGNED_TO_PICKUP);
        assertThat(parcel.toSnapshot().legs()).extracting(LegReached::leg).containsExactly(Leg.L1, Leg.L3);
    }

    @Test
    void ignoresALegThatIsAlreadyRecorded() {
        ParcelEditor parcel = registered();
        int eventsBefore = parcel.events.size();

        parcel.applyNotification(CarrierFixture.aLegObservation(Leg.L1));

        assertThat(parcel.events).hasSize(eventsBefore);
        assertThat(parcel.toSnapshot().legs()).hasSize(1);
    }

    @Test
    void ignoresALegBehindTheOneAlreadyReached() {
        ParcelEditor parcel = registered();
        parcel.applyNotification(CarrierFixture.aLegObservation(Leg.L5));

        parcel.applyNotification(CarrierFixture.aLegObservation(Leg.L3));

        assertThat(parcel.toSnapshot().legState()).isEqualTo(LegState.IN_SORTING_FACILITY);
    }

    @Test
    void storesAnUnmappedStatusWithoutMovingTheLegState() {
        ParcelEditor parcel = registered();

        parcel.applyNotification(LegObservation.unmapped(
                CarrierFixture.REACHED_AT, "ODD", "{\"status\":\"ODD\"}"));

        assertThat(parcel.toSnapshot().legState()).isEqualTo(LegState.PARCEL_REGISTERED);
        assertThat(parcel.toSnapshot().unmappedPayloads())
                .anySatisfy(payload -> assertThat(payload).contains("ODD"));
        assertThat(parcel.events).last()
                .isInstanceOf(DomainEvent.DeliveryStatusUnrecognised.class);
    }

    @Test
    void storesTheSameUnmappedPayloadOnce() {        ParcelEditor parcel = registered();
        LegObservation odd = LegObservation.unmapped(CarrierFixture.REACHED_AT, "ODD", "{\"status\":\"ODD\"}");
        parcel.applyNotification(odd);
        int eventsAfterFirst = parcel.events.size();

        parcel.applyNotification(odd);

        assertThat(parcel.events).hasSize(eventsAfterFirst);
    }

    @Test
    void refusesAMissingObservation() {
        ParcelEditor parcel = registered();

        assertThatThrownBy(() -> parcel.applyNotification(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void refusesAMissingCollectionHandle() {
        ParcelEditor parcel = registered();

        assertThatThrownBy(() -> parcel.markReady(null)).isInstanceOf(NullPointerException.class);
        assertThat(parcel.toSnapshot().collectionHandle()).isNull();
    }

    private static ParcelEditor registered() {
        return ParcelEditor.registerFor(PARCEL_ID, ProcessingFixture.PROCESSING_ID,
                CarrierFixture.aRegistrationRequest(), CarrierFixture.aRegistration(), CarrierFixture.REACHED_AT);
    }

    @Test
    void keepsEveryStatusItCouldNotMap() {
        ParcelEditor parcel = registered();

        parcel.applyNotification(LegObservation.unmapped(CarrierFixture.REACHED_AT, "ODD", "{\"status\":\"ODD\"}"));
        parcel.applyNotification(LegObservation.unmapped(CarrierFixture.REACHED_AT, "ODDER", "{\"status\":\"ODDER\"}"));

        assertThat(parcel.toSnapshot().unmappedPayloads())
                .contains("{\"status\":\"ODD\"}", "{\"status\":\"ODDER\"}");
        assertThat(parcel.events).filteredOn(DomainEvent.DeliveryStatusUnrecognised.class::isInstance).hasSize(2);
    }

    @Test
    void keepsTheCarrierIdentityItWasRegisteredWith() {
        ParcelEditor parcel = registered();
        CarrierIdentity identity = parcel.toSnapshot().carrierIdentity();

        parcel.applyNotification(CarrierFixture.aLegObservation(Leg.L6));

        assertThat(parcel.toSnapshot().carrierIdentity()).isEqualTo(identity);
        assertThat(parcel.toSnapshot().legState()).isEqualTo(LegState.DELIVERED_TO_CUSTOMER);
    }

    @Test
    void readsTheRegisteredAtInstantAsTheFirstLeg() {
        Instant registeredAt = Instant.parse("2026-11-19T10:00:00Z");

        ParcelEditor parcel = ParcelEditor.registerFor(PARCEL_ID, ProcessingFixture.PROCESSING_ID,
                CarrierFixture.aRegistrationRequest(), CarrierFixture.aRegistration(), registeredAt);

        assertThat(parcel.toSnapshot().legs()).singleElement()
                .satisfies(leg -> assertThat(leg.at()).isEqualTo(registeredAt));
    }
}
