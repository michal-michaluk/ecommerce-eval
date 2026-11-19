package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.ParcelState;
import shop.fulfillment.kernel.Parcels;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The commands of the processing resource: one scan closes one position, the bulk packing step puts the picked positions into
 * the box, and readiness declares the order done — the first scan also registers the parcel, and a grant also asks the carrier
 * to collect (register D11, element 08).
 *
 * <p>It holds no rule. Which transition a scan is allowed to make, whether the order is ready and whether a parcel exists to
 * collect are {@link OrderFulfillmentProcess}'s, {@link ReadyForDeliveryDecision}'s and the parcel's; this class loads the
 * processing, applies the command and saves it in one transaction, so a refused command leaves nothing behind and a repeated
 * one answers the state it already had (element 02 RULE-6, element 11 D-11-2).
 *
 * <p>The parcel is reached through {@link Parcels} and never through the parcel slice: the two write slices do not see one
 * another, so the collection request is a value-object question asked of the shared kernel's contract port.
 */
@Service
@Transactional
class ProcessingService {

    private final OrderProcessingRepository processings;
    private final Parcels parcels;

    ProcessingService(OrderProcessingRepository processings, Parcels parcels) {
        this.processings = processings;
        this.parcels = parcels;
    }

    /**
     * Confirms one position by scanning it, and registers the order's one parcel when this scan is the first one — the
     * {@code assigned → picking} edge of element 02 is where "the very beginning" of element 03 sits (register D11).
     *
     * @param processingId  the processing being worked
     * @param productNumber the product the scan named
     * @param employeeId    the caller, who must be the assignee (element 16)
     * @return the processing as the snapshot renders it
     */
    ProcessingSnapshot pick(String processingId, String productNumber, String employeeId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        boolean pickingStarts = processing.toSnapshot().state() == ProcessingState.ASSIGNED;
        processing.pick(productNumber, employeeId);
        registerParcelWhenPickingStarts(processing, pickingStarts);
        return saved(processing);
    }

    /**
     * Puts the named picked positions into the box.
     *
     * @param processingId   the processing being worked
     * @param productNumbers the positions the employee packed
     * @param employeeId     the caller, who must be the assignee (element 16)
     * @return the processing as the snapshot renders it
     */
    ProcessingSnapshot pack(String processingId, List<String> productNumbers, String employeeId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        productNumbers.forEach(productNumber -> processing.pack(productNumber, employeeId));
        return saved(processing);
    }

    /**
     * Declares the order done — one command, two effects, local first: the order is marked ready for delivery and the carrier
     * is asked to collect the parcel it already knows about (element 08 RULE-1).
     *
     * <p>A replay is a no-op: the decision answers NO-OP once the order crossed into {@code readyForDelivery}, so nothing is
     * asked of the carrier a second time (RULE-5). A refusal is the decision's own, with its own reason, and writes nothing.
     * The verdict is read from the aggregate rather than re-derived from the table's conditions, so a new grant row could not
     * silently skip the collection request.
     *
     * @param processingId the processing being marked ready
     * @return the processing as the snapshot renders it, read **after** both effects, so the parcel it carries is the one the
     *         command left behind
     */
    ProcessingSnapshot markReady(String processingId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        Optional<ParcelState> parcel = parcels.registeredFor(processing.processingId);
        ReadyForDeliveryDecision.Verdict verdict = processing.markReady(parcel.map(ParcelState::parcelId).orElse(null));
        processings.save(processing);
        collectWhenGranted(parcel, verdict);
        return asSnapshot(processing);
    }

    /** RF2: the carrier is asked once, and only after the local mark of RF1 was written. */
    private void collectWhenGranted(Optional<ParcelState> parcel, ReadyForDeliveryDecision.Verdict verdict) {
        if (verdict instanceof ReadyForDeliveryDecision.Verdict.Grant) {
            parcel.ifPresent(registered -> parcels.requestCollection(registered.parcelId()));
        }
    }

    private void registerParcelWhenPickingStarts(OrderFulfillmentProcess processing, boolean pickingStarts) {
        if (pickingStarts) {
            OrderProcessing scanned = processing.toSnapshot();
            parcels.register(scanned.processingId(), scanned.order().delivery());
        }
    }

    /** RF1 is written before RF2 is asked, as element 08 orders the two effects of one command. */
    private ProcessingSnapshot saved(OrderFulfillmentProcess processing) {
        processings.save(processing);
        return asSnapshot(processing);
    }

    private ProcessingSnapshot asSnapshot(OrderFulfillmentProcess processing) {
        OrderProcessing snapshot = processing.toSnapshot();
        return ProcessingSnapshot.of(snapshot, parcels.registeredFor(snapshot.processingId()));
    }

    private OrderFulfillmentProcess processingOf(String processingId) {
        return processings.get(processingId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }
}
