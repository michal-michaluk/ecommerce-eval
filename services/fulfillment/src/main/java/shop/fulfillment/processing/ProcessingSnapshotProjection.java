package shop.fulfillment.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Parcels;

/**
 * The processing resource of element 11 as a query handler: load the aggregate, read the parcel it carries and render the
 * snapshot.
 *
 * <p>It is the read half of the resource, so the {@code GET} route depends on this projection and not on
 * {@link ProcessingService} ({@code http.no-write-model-in-read}); a resolver opening an order's processing state and an
 * employee re-rendering the screen after a scan read exactly the same payload a command answers.
 *
 * <p>The parcel arrives through {@link Parcels} — the kernel's contract port — so the payload carries a carrier identity the
 * processing slice never stores, and the parcel's own record stays the parcel slice's.
 */
@Component
@Transactional
class ProcessingSnapshotProjection {

    private final OrderProcessingRepository processings;
    private final Parcels parcels;

    ProcessingSnapshotProjection(OrderProcessingRepository processings, Parcels parcels) {
        this.processings = processings;
        this.parcels = parcels;
    }

    /**
     * The snapshot of one processing.
     *
     * @param processingId the processing to read
     * @return the payload of element 11
     */
    @Transactional(readOnly = true)
    public ProcessingSnapshot findProcessing(String processingId) {
        return processings.get(processingId)
                .map(OrderFulfillmentProcess::toSnapshot)
                .map(this::rendered)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }

    private ProcessingSnapshot rendered(OrderProcessing processing) {
        return ProcessingSnapshot.of(processing, parcels.registeredFor(processing.processingId()));
    }
}
