package shop.fulfillment.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The processing resource of element 11 as a query handler: load the aggregate and render its snapshot.
 *
 * <p>It is the read half of the resource, so the {@code GET} route depends on this projection and not on
 * {@link ProcessingService} ({@code http.no-write-model-in-read}); a resolver opening an order's processing state and an
 * employee re-rendering the screen after a scan read exactly the same payload a command answers.
 */
@Component
@Transactional
class ProcessingSnapshotProjection {

    private final OrderProcessingRepository processings;

    ProcessingSnapshotProjection(OrderProcessingRepository processings) {
        this.processings = processings;
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
                .map(ProcessingSnapshot::of)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }
}
