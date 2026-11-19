package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The picking and packing use cases of element 02: one scan closes one position, and the bulk packing step puts the picked
 * positions into the box.
 *
 * <p>It holds no rule. Which transition a scan is allowed to make, whether the order may be worked at all and what a
 * packed position set means are the aggregate's — this class loads the processing, applies the command and saves it in one
 * transaction, so a refused command leaves nothing behind and a repeated one answers the state it already had
 * (element 02 RULE-6, element 11 D-11-2).
 */
@Service
@Transactional
class ProcessingService {

    private final OrderProcessingRepository processings;

    ProcessingService(OrderProcessingRepository processings) {
        this.processings = processings;
    }

    /**
     * Confirms one position by scanning it.
     *
     * @param processingId  the processing being worked
     * @param productNumber the product the scan named
     * @param employeeId    the caller, who must be the assignee (element 16)
     * @return the processing as the snapshot renders it
     */
    OrderProcessing pick(String processingId, String productNumber, String employeeId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        processing.pick(productNumber, employeeId);
        processings.save(processing);
        return processing.toSnapshot();
    }

    /**
     * Puts the named picked positions into the box.
     *
     * @param processingId   the processing being worked
     * @param productNumbers the positions the employee packed
     * @param employeeId     the caller, who must be the assignee (element 16)
     * @return the processing as the snapshot renders it
     */
    OrderProcessing pack(String processingId, List<String> productNumbers, String employeeId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        productNumbers.forEach(productNumber -> processing.pack(productNumber, employeeId));
        processings.save(processing);
        return processing.toSnapshot();
    }

    private OrderFulfillmentProcess processingOf(String processingId) {
        return processings.get(processingId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }
}
