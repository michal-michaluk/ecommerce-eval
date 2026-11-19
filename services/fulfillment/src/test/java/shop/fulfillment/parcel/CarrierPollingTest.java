package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.LegReached;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 14's status read as one job: every leg the carrier reports is applied forward only, the same leg applied twice
 * changes nothing, a status we cannot map moves nothing and is kept with its payload, and a deployment without a carrier polls
 * nobody.
 */
class CarrierPollingTest {

    static final ParcelId PARCEL_ID = new ParcelId("PCL-2026-0001");

    final FakeParcelRepository parcels = new FakeParcelRepository();
    final FakeCarrierPort carrier = new FakeCarrierPort();
    final ParcelService service = ParcelFixture.service(parcels, carrier);

    @Test
    void appliesEveryLegTheCarrierReports() {
        registered();
        carrier.withStatuses(List.of(CarrierFixture.aLegObservation(Leg.L3), CarrierFixture.aLegObservation(Leg.L4)));

        ParcelFixture.poller(parcels, carrier).poll();

        assertThat(legs()).extracting(LegReached::leg).containsExactly(Leg.L1, Leg.L3, Leg.L4);
        assertThat(parcel().toSnapshot().legState()).isEqualTo(LegState.PICKED_UP);
    }

    @Test
    void appliesTheSameLegTwiceOnlyOnce() {
        registered();
        carrier.withStatuses(List.of(CarrierFixture.aLegObservation(Leg.L3)));
        ParcelFixture.poller(parcels, carrier).poll();
        int stored = parcels.saved.size();

        ParcelFixture.poller(parcels, carrier).poll();

        assertThat(legs()).hasSize(2);
        assertThat(parcels.saved).hasSize(stored);
    }

    @Test
    void neverMovesALegBackwards() {
        registered();
        carrier.withStatuses(List.of(CarrierFixture.aLegObservation(Leg.L5)));
        ParcelFixture.poller(parcels, carrier).poll();

        carrier.withStatuses(List.of(CarrierFixture.aLegObservation(Leg.L3)));
        ParcelFixture.poller(parcels, carrier).poll();

        assertThat(parcel().toSnapshot().legState()).isEqualTo(LegState.IN_SORTING_FACILITY);
        assertThat(legs()).extracting(LegReached::leg).containsExactly(Leg.L1, Leg.L5);
    }

    @Test
    void keepsAStatusWeCannotMapWithoutMovingTheParcel() {
        registered();
        carrier.withStatuses(List.of(LegObservation.unmapped(
                CarrierFixture.REACHED_AT, "ODD", "{\"status\":\"ODD\"}")));

        ParcelFixture.poller(parcels, carrier).poll();

        assertThat(parcel().toSnapshot().legState()).isEqualTo(LegState.PARCEL_REGISTERED);
        assertThat(parcel().toSnapshot().unmappedPayloads())
                .anySatisfy(payload -> assertThat(payload).contains("ODD"));
        assertThat(parcels.emitted).last()
                .isInstanceOf(DomainEvent.DeliveryStatusUnrecognised.class);
    }

    @Test
    void pollsNothingWithoutACarrier() {
        registered();

        ParcelFixture.poller(parcels, null).poll();

        assertThat(carrier.callsStartingWith("status")).isEmpty();
        assertThat(legs()).hasSize(1);
    }

    @Test
    void stopsPollingADeliveredParcel() {
        registered();
        carrier.withStatuses(List.of(CarrierFixture.aLegObservation(Leg.L6)));
        ParcelFixture.poller(parcels, carrier).poll();
        int callsAfterDelivery = carrier.calls.size();

        ParcelFixture.poller(parcels, carrier).poll();

        assertThat(carrier.calls).hasSize(callsAfterDelivery);
    }

    @Test
    void surfacesACarrierFailureInsteadOfSwallowingIt() {
        registered();
        FakeCarrierPort failing = new FakeCarrierPort().failing(new IllegalStateException("carrier is down"));

        assertThatThrownBy(() -> ParcelFixture.poller(parcels, failing).poll())
                .isInstanceOf(IllegalStateException.class);
    }

    private void registered() {
        service.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
    }

    private ParcelEditor parcel() {
        return parcels.stored(PARCEL_ID);
    }

    private List<LegReached> legs() {
        return parcel().toSnapshot().legs();
    }
}
