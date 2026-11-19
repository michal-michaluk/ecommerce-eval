package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.RegistrationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Elements 03 and 14 behind the two parcel commands: exactly one parcel is registered for a processing, with our reference,
 * one of one, the configured weight and sender and the order's own delivery target, and it is collected once.
 */
class ParcelServiceTest {

    static final OrderProcessingId PROCESSING_ID = ProcessingFixture.PROCESSING_ID;

    final FakeParcelRepository parcels = new FakeParcelRepository();
    final FakeCarrierPort carrier = new FakeCarrierPort();
    final ParcelService service = ParcelFixture.service(parcels, carrier);

    @Test
    void registersOneParcelWithOurReferenceAndOneOfOne() {
        ParcelId parcelId = service.register(PROCESSING_ID, OrderFixture.aDelivery());

        RegistrationRequest registered = carrier.registered.get(0);
        assertThat(parcelId.value()).isEqualTo("PCL-2026-0001");
        assertThat(registered.reference().value()).isEqualTo("OP-2026-0001-1");
        assertThat(registered.ofTotal()).isEqualTo(1);
        assertThat(registered.weightGrams()).isEqualTo(ParcelFixture.WEIGHT_GRAMS);
        assertThat(registered.serviceCode()).isEqualTo(OrderFixture.aDelivery().methodCode());
        assertThat(registered.pickupPointId()).isEqualTo("KRA01M");
        assertThat(registered.receiver()).isNull();
        assertThat(registered.sender().city()).isEqualTo(ParcelFixture.aRegistration().senderCity());
    }

    @Test
    void storesTheIdentityTheCarrierAssignedAndNothingElse() {
        service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(service.registeredFor(PROCESSING_ID)).get()
                .satisfies(parcel -> {
                    assertThat(parcel.carrierIdentity()).isEqualTo(carrier.identityOf("OP-2026-0001-1"));
                    assertThat(parcel.legState()).isEqualTo(LegState.PARCEL_REGISTERED);
                });
    }

    @Test
    void announcesTheDeliveryNumberItWasGiven() {
        service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(parcels.emitted)
                .singleElement()
                .isInstanceOf(DomainEvent.DeliveryNumberGenerated.class);
    }

    @Test
    void answersTheParcelItAlreadyHasAndConsumesNoSecondNumber() {
        ParcelId first = service.register(PROCESSING_ID, OrderFixture.aDelivery());

        ParcelId repeated = service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(repeated).isEqualTo(first);
        assertThat(carrier.callsStartingWith("register")).hasSize(1);
        assertThat(parcels.saved).hasSize(1);
    }

    @Test
    void refusesToRegisterWithoutACarrier() {
        ParcelService unwired = ParcelFixture.service(parcels, null);

        assertThatThrownBy(() -> unwired.register(PROCESSING_ID, OrderFixture.aDelivery()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.CARRIER_NOT_WIRED));
        assertThat(parcels.parcels).isEmpty();
    }

    @Test
    void asksAgainWithTheSameReferenceWhenTheCarrierFailed() {
        FakeCarrierPort failing = new FakeCarrierPort().failing(new IllegalStateException("carrier is down"));

        assertThatThrownBy(() -> ParcelFixture.service(parcels, failing).register(PROCESSING_ID, OrderFixture.aDelivery()))
                .isInstanceOf(IllegalStateException.class);
        service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(failing.calls).containsExactly("register OP-2026-0001-1");
        assertThat(carrier.calls).containsExactly("register OP-2026-0001-1");
    }

    @Test
    void asksForOneCollectionAndNeverForASecond() {
        ParcelId parcelId = service.register(PROCESSING_ID, OrderFixture.aDelivery());

        service.requestCollection(parcelId);
        service.requestCollection(parcelId);

        assertThat(carrier.callsStartingWith("requestCollection")).hasSize(1);
        assertThat(parcels.stored(parcelId).toSnapshot().collectionHandle())
                .isEqualTo(new CollectionHandle(FakeCarrierPort.COLLECTION));
        assertThat(parcels.stored(parcelId).toSnapshot().legState()).isEqualTo(LegState.PARCEL_READY);
    }

    @Test
    void refusesToCollectWithoutACarrier() {
        ParcelId parcelId = service.register(PROCESSING_ID, OrderFixture.aDelivery());
        ParcelService unwired = ParcelFixture.service(parcels, null);

        assertThatThrownBy(() -> unwired.requestCollection(parcelId))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.CARRIER_NOT_WIRED));
        assertThat(parcels.stored(parcelId).toSnapshot().legState()).isEqualTo(LegState.PARCEL_REGISTERED);
    }

    @Test
    void collectsNothingForAParcelNobodyRegistered() {
        service.requestCollection(new ParcelId("PCL-2026-0404"));

        assertThat(carrier.calls).isEmpty();
    }

    @Test
    void keepsTheParcelTheSnapshotDescribes() {
        service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(service.registeredFor(PROCESSING_ID)).get()
                .satisfies(parcel -> assertThat(parcel.reference().value()).isEqualTo("OP-2026-0001-1"));
        assertThat(service.registeredFor(ProcessingFixture.OTHER_PROCESSING_ID)).isEmpty();
    }

    @Test
    void answersEveryParcelStillOnItsWay() {
        service.register(PROCESSING_ID, OrderFixture.aDelivery());

        assertThat(parcels.awaitingDelivery()).hasSize(1);
        assertThat(List.of(parcels.stored(new ParcelId("PCL-2026-0001")).toSnapshot().legState()))
                .containsExactly(LegState.PARCEL_REGISTERED);
    }
}
