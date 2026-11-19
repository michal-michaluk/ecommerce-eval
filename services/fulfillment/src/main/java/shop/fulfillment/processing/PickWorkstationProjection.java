package shop.fulfillment.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The pick workstation of element 16 as a query handler: the processing once, the walk once, and the two composed into the
 * screen the assigned employee works from.
 *
 * <p>The two regions of W1 come from the two elements that own them — positions and counters from element 02, locations and
 * walk order from element 10 — and this projection adds no fact of its own: it reads the aggregate once so one request is
 * one load, and it reuses the pick list's query handler so the walk an employee sees on the screen is the walk
 * {@code GET …/pick-list} serves, not a second calculation.
 *
 * <p>A warehouse the pick list cannot read means there is no screen at all: scanning is refused with the reason shown, and
 * no offline or cached list is invented (element 16 states, element 13 RULE-7).
 */
@Component
@Transactional
class PickWorkstationProjection {

    private final OrderProcessingRepository processings;
    private final PickListProjection pickLists;

    PickWorkstationProjection(OrderProcessingRepository processings, PickListProjection pickLists) {
        this.processings = processings;
        this.pickLists = pickLists;
    }

    /**
     * The screen of one processing.
     *
     * @param processingId the order being worked
     * @return the read model of element 16
     */
    @Transactional(readOnly = true)
    public PickWorkstation findWorkstation(String processingId) {
        OrderProcessing processing = processings.get(processingId)
                .map(OrderFulfillmentProcess::toSnapshot)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
        return PickWorkstation.of(processing, pickLists.findPickList(processing));
    }
}
