package shop.fulfillment.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.Delivery;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.ParcelState;
import shop.fulfillment.kernel.Parcels;

/**
 * The kernel's {@link Parcels} contract port as a test double: one parcel per processing (register D10), every registration
 * and every collection request recorded, so a service test proves the orchestration without the parcel slice.
 */
class FakeParcels implements Parcels {

    static final CarrierIdentity WAYBILL = new CarrierIdentity("0234567890123");

    final Map<String, ParcelId> registered = new LinkedHashMap<>();
    final List<String> collections = new ArrayList<>();
    final List<Delivery> deliveries = new ArrayList<>();
    private int minted;

    @Override
    public Optional<ParcelState> registeredFor(OrderProcessingId processingId) {
        return Optional.ofNullable(registered.get(processingId.value()))
                .map(parcelId -> CarrierFixture.aParcelStateOf(parcelId,
                        new ParcelReference(processingId.value() + "-1"), legStateOf(parcelId)));
    }

    /** Mirrors the port's semantics: a parcel that was collected answers {@code parcelReady}, not {@code parcelRegistered}. */
    private LegState legStateOf(ParcelId parcelId) {
        return collections.contains(parcelId.value()) ? LegState.PARCEL_READY : LegState.PARCEL_REGISTERED;
    }

    @Override
    public ParcelId register(OrderProcessingId processingId, Delivery delivery) {
        return registered.computeIfAbsent(processingId.value(), id -> {
            deliveries.add(delivery);
            return new ParcelId("PCL-2026-%04d".formatted(++minted));
        });
    }

    @Override
    public void requestCollection(ParcelId parcelId) {
        collections.add(parcelId.value());
    }

    int registrations() {
        return registered.size();
    }
}
