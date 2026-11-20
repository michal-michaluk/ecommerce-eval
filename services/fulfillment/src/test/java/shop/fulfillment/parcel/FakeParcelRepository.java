package shop.fulfillment.parcel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.ParcelId;

/**
 * The {@link ParcelRepository} port backed by maps: the same find-or-create lifecycle the adapter has, without a database, so a
 * unit test asserts the use case and not the storage.
 *
 * <p>It mirrors the three things a test has to see — what was stored, how many writes the use case made, and which labels were
 * kept — and it drains the aggregate's buffer on save the way the adapter does, so the events a test asserts on are the ones
 * that were published.
 */
class FakeParcelRepository implements ParcelRepository {

    final Map<String, ParcelEditor> parcels = new LinkedHashMap<>();
    final Map<String, LabelDocument> labels = new LinkedHashMap<>();
    final List<String> saved = new ArrayList<>();
    final List<DomainEvent> emitted = new ArrayList<>();
    private int minted;

    @Override
    public Optional<ParcelEditor> get(ParcelId parcelId) {
        return Optional.ofNullable(parcels.get(parcelId.value()));
    }

    @Override
    public Optional<ParcelEditor> findByProcessing(String processingId) {
        return parcels.values().stream()
                .filter(parcel -> processingId.equals(parcel.toSnapshot().processingId().value()))
                .findFirst();
    }

    @Override
    public List<ParcelEditor> awaitingDelivery() {
        return parcels.values().stream()
                .filter(parcel -> parcel.toSnapshot().legState() != LegState.DELIVERED_TO_CUSTOMER)
                .toList();
    }

    @Override
    public ParcelId nextIdentity() {
        return new ParcelId("PCL-2026-%04d".formatted(++minted));
    }

    @Override
    public void save(ParcelEditor parcel) {
        emitted.addAll(parcel.events);
        parcel.events.clear();
        parcels.put(parcel.parcelId.value(), parcel);
        saved.add(parcel.parcelId.value());
    }

    @Override
    public Optional<LabelDocument> labelOf(ParcelId parcelId) {
        return Optional.ofNullable(labels.get(parcelId.value()));
    }

    @Override
    public void storeLabel(ParcelId parcelId, LabelDocument label) {
        labels.put(parcelId.value(), label);
    }

    void store(ParcelEditor parcel) {
        parcels.put(parcel.parcelId.value(), parcel);
    }

    ParcelEditor stored(ParcelId parcelId) {
        return parcels.get(parcelId.value());
    }
}
