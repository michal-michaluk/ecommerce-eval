package shop.fulfillment.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The claim of element 07: one load, one command on the aggregate, one save — and, for the claim that is granted, the
 * warehouse reservation of register D11.
 *
 * <p>The verdict is {@link AssignmentGuard}'s, read once and never restated: this class applies it through the aggregate and
 * asks the warehouse only when it is a grant. A repeated claim by the owner is the no-op of DT-07-4 and reserves nothing; a
 * refusal never reaches the warehouse at all.
 *
 * <p>Both writes happen in the one transaction the service owns — the claim and every reservation — so a warehouse that
 * refuses, fails or loses an optimistic lock leaves no employee holding an unreserved order and no allocation behind
 * (element 13 RULE-7, {@link WarehouseReservationsIntegration}).
 */
@Service
@Transactional
class AssignmentService {

    private final OrderProcessingRepository processings;
    private final WarehouseReservationsIntegration reservations;

    AssignmentService(OrderProcessingRepository processings, WarehouseReservationsIntegration reservations) {
        this.processings = processings;
        this.reservations = reservations;
    }

    /**
     * Claims one order for one employee.
     *
     * @param processingId the processing the claim addresses
     * @param employeeId   the caller, who becomes the assignee on a grant
     * @return the processing and whether the claim took it
     */
    OrderClaim claim(String processingId, String employeeId) {
        OrderFulfillmentProcess processing = claimedProcessing(processingId);
        OrderProcessing before = processing.toSnapshot();
        AssignmentGuard.Verdict verdict = AssignmentGuard.decide(before.state(), before.assignee(), employeeId);
        OrderProcessing claimed = claimAndReserve(processing, employeeId, verdict);
        return new OrderClaim(claimed, verdict instanceof AssignmentGuard.Verdict.Grant);
    }

    private OrderFulfillmentProcess claimedProcessing(String processingId) {
        return processings.get(processingId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId));
    }

    /** The command and its write: the aggregate applies the verdict the guard gave, and the claim is stored. */
    private OrderProcessing claimAndReserve(OrderFulfillmentProcess processing, String employeeId,
                                            AssignmentGuard.Verdict verdict) {
        processing.assignTo(employeeId);
        processings.save(processing);
        OrderProcessing claimed = processing.toSnapshot();
        reserveWhenGranted(verdict, claimed);
        return claimed;
    }

    /** Register D11: the claim that moved the assignee is the one that reserves, so a repeat reserves nothing. */
    private void reserveWhenGranted(AssignmentGuard.Verdict verdict, OrderProcessing claimed) {
        if (verdict instanceof AssignmentGuard.Verdict.Grant) {
            reservations.reserveClaim(claimed);
        }
    }
}
