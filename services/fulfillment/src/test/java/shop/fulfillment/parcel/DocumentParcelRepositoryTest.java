package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.RegistrationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The parcel's document adapter: the aggregate round-trips through its JSONB snapshot, the identity is minted from the
 * sequence, the label is stored as the bytes the carrier printed, and the poll finds the parcels still on their way.
 */
@IntegrationTest
class DocumentParcelRepositoryTest {

    static final ParcelId PARCEL_ID = ProcessingFixture.PARCEL_ID;

    @Autowired
    DocumentParcelRepository parcels;

    @Test
    void storesTheAggregateAndReadsItBackWithItsLegs() {
        parcels.save(aRegisteredParcel());

        assertThat(parcels.get(PARCEL_ID)).get().satisfies(parcel -> {
            Parcel snapshot = parcel.toSnapshot();
            assertThat(snapshot.parcelId()).isEqualTo(PARCEL_ID);
            assertThat(snapshot.processingId()).isEqualTo(ProcessingFixture.PROCESSING_ID);
            assertThat(snapshot.reference()).isEqualTo(CarrierFixture.REFERENCE);
            assertThat(snapshot.carrierIdentity()).isEqualTo(CarrierFixture.IDENTITY);
            assertThat(snapshot.legs()).hasSize(1);
            assertThat(snapshot.legState()).isEqualTo(LegState.PARCEL_REGISTERED);
        });
        assertThat(parcels.findByProcessing(ProcessingFixture.PROCESSING_ID.value())).isPresent();
    }

    @Test
    void appliesALegToTheStoredAggregate() {
        parcels.save(aRegisteredParcel());
        ParcelEditor stored = parcels.get(PARCEL_ID).orElseThrow();

        stored.applyNotification(CarrierFixture.aLegObservation(Leg.L3));
        parcels.save(stored);

        assertThat(parcels.get(PARCEL_ID).orElseThrow().toSnapshot().legState())
                .isEqualTo(LegState.ASSIGNED_TO_PICKUP);
    }

    @Test
    void mintsAnIdentityAndFindsTheParcelsStillOnTheirWay() {
        ParcelId minted = parcels.nextIdentity();

        assertThat(minted.value()).matches("PCL-\\d{4}-\\d{4}");
        parcels.save(aRegisteredParcel());
        assertThat(parcels.awaitingDelivery()).extracting(parcel -> parcel.toSnapshot().parcelId())
                .contains(PARCEL_ID);
    }

    @Test
    void storesTheCarriersDocumentByteForByte() {
        parcels.storeLabel(PARCEL_ID, CarrierFixture.aLabelDocument());

        assertThat(parcels.labelOf(PARCEL_ID)).get()
                .satisfies(label -> {
                    assertThat(label.bytes()).isEqualTo(CarrierFixture.aLabelDocument().bytes());
                    assertThat(label.format()).isEqualTo(CarrierFixture.aLabelDocument().format());
                });
    }

    @Test
    void publishesTheEventsOfOneWriteOnce() {
        ParcelEditor parcel = aRegisteredParcel();

        List<DomainEvent> emitted = DocumentParcelRepository.eventsEmittedFrom(parcel);

        assertThat(emitted).singleElement().isInstanceOf(DomainEvent.DeliveryNumberGenerated.class);
        assertThat(parcel.events).isEmpty();
    }

    private static ParcelEditor aRegisteredParcel() {
        RegistrationRequest request = CarrierFixture.aRegistrationRequest();
        return ParcelEditor.registerFor(PARCEL_ID, ProcessingFixture.PROCESSING_ID, request,
                CarrierFixture.aRegistration(), OrderFixture.PLACED_AT);
    }
}
