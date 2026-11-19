package shop.fulfillment.parcel;

import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.ParcelId;

/**
 * The one secondary port of the parcel aggregate: the adapter rehydrates {@link ParcelEditor} and saves it back, whichever
 * storage shape it uses ({@code persistence-adapters.md}).
 *
 * <p>Three questions are asked of it beyond the lifecycle, and each is a fact only this boundary owns:
 * {@link #findByProcessing} because the processing lane addresses a parcel by its order and not by the parcel's own id,
 * {@link #awaitingDelivery} because the status poll has to walk the parcels still on their way — the carrier cannot notify
 * us, so the set is ours to read — and the two label methods because the carrier's printed document is a stored artefact
 * rather than a value of the aggregate ({@code 06-consignment-note.md} RULE-9). {@link #nextIdentity} mints the parcel's
 * own id at storage, the way the processing's is minted.
 */
interface ParcelRepository {

    Optional<ParcelEditor> get(ParcelId parcelId);

    Optional<ParcelEditor> findByProcessing(String processingId);

    List<ParcelEditor> awaitingDelivery();

    ParcelId nextIdentity();

    void save(ParcelEditor parcel);

    Optional<LabelDocument> labelOf(ParcelId parcelId);

    void storeLabel(ParcelId parcelId, LabelDocument label);
}
