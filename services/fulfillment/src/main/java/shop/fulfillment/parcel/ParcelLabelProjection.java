package shop.fulfillment.parcel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.LabelRequest;
import shop.fulfillment.kernel.ParcelState;

/**
 * The consignment note of element 06 as a stored document: the carrier's own print, asked for once and served back byte for
 * byte.
 *
 * <p>The service never authors a carrier-owned field and never renders a template of ours — the request names the parcel we
 * already hold by its waybill and the answer is the carrier's document (element 06 RULE-2, element 14 RULE-3). A print is not
 * a registration: no number is consumed, no parcel changes and nothing is registered again, so a reprint of a parcel whose
 * document we already stored is served without a call to the carrier at all.
 *
 * <p>The first print asks the carrier for the same waybill we hold and stores what comes back — element 06 RULE-9's default for
 * an open question ({@code UND-12}, Q-06-1). A parcel that does not exist yet has no label, and that is the contract's own
 * {@code 404 PROCESSING_NOT_FOUND} rather than an empty document: one 404 code per {@code /processing/{id}/…} route, so a client
 * branching on it never misses this one.
 */
@Component
@Transactional
class ParcelLabelProjection {

    static final String FORMAT = "PDF";
    static final String LAYOUT = "A4";

    private final ParcelRepository parcels;
    private final Optional<CarrierPort> carrier;

    ParcelLabelProjection(ParcelRepository parcels, Optional<CarrierPort> carrier) {
        this.parcels = parcels;
        this.carrier = carrier;
    }

    /**
     * The label of one parcel: the bytes we stored, or — the first time it is asked for — the document the carrier renders.
     *
     * @param processingId the processing whose parcel is printed
     * @return the carrier's document
     */
    LabelDocument labelOf(String processingId) {
        ParcelEditor parcel = parcels.findByProcessing(processingId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
        return parcels.labelOf(parcel.toParcelState().parcelId()).orElseGet(() -> rendered(parcel));
    }

    private LabelDocument rendered(ParcelEditor parcel) {
        ParcelState parcelState = parcel.toParcelState();
        LabelDocument document = carrier().label(new LabelRequest(parcelState.reference(),
                parcelState.carrierIdentity(), FORMAT, LAYOUT));
        parcels.storeLabel(parcelState.parcelId(), document);
        return document;
    }

    private CarrierPort carrier() {
        return carrier.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.CARRIER_NOT_WIRED, "printing a label"));
    }
}
