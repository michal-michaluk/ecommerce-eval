package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.FulfillmentIssue;
import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.ParcelState;
import shop.fulfillment.kernel.Parcels;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The commands of the processing resource: one scan closes one position, the bulk packing step puts the picked positions into
 * the box, readiness declares the order done — the first scan also registers the parcel, and a grant also asks the carrier to
 * collect and books the goods out of the warehouse (register D11, element 08) — one issue holds the whole order, and one resume
 * lifts the hold (elements 04 and 09).
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
    private final WarehouseGoodsIssueIntegration goodsIssue;
    private final Clock clock;

    ProcessingService(OrderProcessingRepository processings, Parcels parcels,
                      WarehouseGoodsIssueIntegration goodsIssue, Clock clock) {
        this.processings = processings;
        this.parcels = parcels;
        this.goodsIssue = goodsIssue;
        this.clock = clock;
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
        applyGrantedEffects(verdict, parcel, processing.toSnapshot());
        return asSnapshot(processing);
    }

    /**
     * Records one warehouse reality gap and lets the hold follow it (elements 04 and 09): the reporter is the caller, the issue
     * id is minted here and the moment is this service's clock. The hold itself is the aggregate's — one issue holds the whole
     * order, a further one joins a hold already placed, and an order that has not started picking is refused.
     *
     * @param processingId the processing the issue is reported on
     * @param command      the kind and the product the employee named
     * @param employeeId   the caller, who must be the assignee (element 04 RULE-2)
     * @return the processing as the snapshot renders it, carrying the issue it just wrote
     */
    ProcessingSnapshot reportIssue(String processingId, ReportIssue command, String employeeId) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        FulfillmentIssue issue = issueOf(processing, command, employeeId);
        processing.reportIssue(issue);
        return saved(processing).withIssueId(issue.issueId().value());
    }

    /**
     * Lifts the hold the named issues vouch for (element 09 H5). Closing an issue is this command's fact and nobody else's
     * (register D8), so an issue left open refuses the resume with the count of what remains (H6) and an id this order does not
     * account for is the unknown resource it is (register D11). An order that is not held is the no-op of H7.
     *
     * @param processingId   the processing leaving hold
     * @param closedIssueIds the issues the caller vouches for, as the request named them
     * @return the processing as the snapshot renders it, carrying the issues this command closed
     */
    ProcessingSnapshot resume(String processingId, List<String> closedIssueIds) {
        OrderFulfillmentProcess processing = processingOf(processingId);
        List<IssueId> named = closedIssueIds.stream().map(IssueId::new).toList();
        List<IssueId> openBefore = openIssuesOf(processing);
        processing.resume(named, clock.instant());
        return saved(processing).withClosedIssueIds(closedBy(processing.toSnapshot(), openBefore, named));
    }

    /** RF2 and RF3, in that order, and only when the decision granted: the carrier collects, the goods leave (D11). */
    private void applyGrantedEffects(ReadyForDeliveryDecision.Verdict verdict, Optional<ParcelState> parcel,
                                     OrderProcessing processing) {
        if (verdict instanceof ReadyForDeliveryDecision.Verdict.Grant) {
            parcel.ifPresent(registered -> parcels.requestCollection(registered.parcelId()));
            goodsIssue.issueClaim(processing);
        }
    }

    private FulfillmentIssue issueOf(OrderFulfillmentProcess processing, ReportIssue command, String employeeId) {
        return FulfillmentIssue.builder()
                .issueId(processings.nextIssueIdentity())
                .processingId(processing.processingId)
                .kind(command.kind())
                .productNumber(command.productNumber())
                .reportedBy(employeeId)
                .reportedAt(clock.instant())
                .build();
    }

    /**
     * The issues **this** command closed: named by the caller and open until it ran. An id it names that was already closed is
     * not a close this command made, so a replayed resume answers nothing closed (element 09 H7).
     */
    private static List<String> closedBy(OrderProcessing processing, List<IssueId> openBefore, List<IssueId> named) {
        return processing.issues().stream()
                .filter(issue -> named.contains(issue.issueId()) && openBefore.contains(issue.issueId()))
                .filter(issue -> !issue.isOpen())
                .map(issue -> issue.issueId().value())
                .toList();
    }

    private static List<IssueId> openIssuesOf(OrderFulfillmentProcess processing) {
        return processing.toSnapshot().issues().stream()
                .filter(FulfillmentIssue::isOpen)
                .map(FulfillmentIssue::issueId)
                .toList();
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
