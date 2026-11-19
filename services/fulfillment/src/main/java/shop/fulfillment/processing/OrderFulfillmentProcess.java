package shop.fulfillment.processing;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.FulfillmentIssue;
import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The processing of one order: who owns it, how far each position got, whether it is on hold, and which parcel
 * carries it (element 02).
 *
 * <p>It exists because the order is immutable — the whole state of the lane lives beside it. Every transition of the
 * state machine is a command of this class and emits the domain event the board names for it; nothing else writes
 * {@code state}, and there is deliberately no edge from {@code PENDING} or {@code ASSIGNED} into a hold, and none out
 * of {@code DELIVERED}.
 *
 * <p>Exclusivity is decided by {@link AssignmentGuard}, readiness by {@link ReadyForDeliveryDecision} and the hold by
 * {@link HoldDecision}: the aggregate applies their verdicts instead of re-implementing their tables. Instances are
 * created through {@link #startProcessing(OrderProcessingId, Order)}; the aggregate is package-private and only the
 * lifecycle in this context drives it.
 */
@AllArgsConstructor
class OrderFulfillmentProcess {

    final OrderProcessingId processingId;

    final List<DomainEvent> events;

    private final Order order;
    private String assignee;
    private ProcessingState state;
    private List<OrderPosition> positions;
    private ParcelId parcelId;
    private ProcessingState heldState;
    private final List<FulfillmentIssue> issues;

    /**
     * Opens the processing of one handed-over order: one position per order line, state {@code pending}, nobody
     * assigned.
     *
     * @param processingId the identity minted for this processing
     * @param order        the finished, immutable order the lane has to fulfil
     * @return a pending processing that has announced its own start
     */
    static OrderFulfillmentProcess startProcessing(OrderProcessingId processingId, Order order) {
        Objects.requireNonNull(order, "order");
        OrderFulfillmentProcess processing = new OrderFulfillmentProcess(processingId, new ArrayList<>(), order,
                null, ProcessingState.PENDING, positionsOf(order), null, null, new ArrayList<>());
        processing.events.add(new DomainEvent.OrderProcessingStarted(processingId, order));
        return processing;
    }

    /**
     * Claims the processing for one employee, or refuses the claim — never queues it (element 07).
     *
     * @param employeeId the employee claiming the order; the claimer becomes the assignee
     */
    void assignTo(String employeeId) {
        AssignmentGuard.Verdict verdict = AssignmentGuard.decide(state, assignee, employeeId);
        ensureClaimIsAccepted(verdict);
        if (verdict instanceof AssignmentGuard.Verdict.Grant) {
            assignee = employeeId;
            state = ProcessingState.ASSIGNED;
            events.add(new DomainEvent.EmployeeAssigned(processingId, employeeId));
        }
    }

    /**
     * Confirms one position by scanning it. The first confirmation moves an assigned order to picking; confirming an
     * already picked or packed position again changes nothing.
     *
     * @param productNumber the product the scan named
     * @param employeeId    the caller, who must be the assignee (element 16: only he confirms)
     */
    void pick(String productNumber, String employeeId) {
        ensureWorkIsInHand("picking", employeeId);
        OrderPosition position = positionOf(productNumber);
        if (position.state() == PositionState.PENDING) {
            positions.set(positions.indexOf(position), position.picked());
            state = state.afterPicking();
            events.add(new DomainEvent.PositionPicked(processingId, productNumber));
        }
    }

    /**
     * Puts one picked position into the box; the last one moves the order to {@code packed}. Packing a position that is
     * already in the box changes nothing — the repeated command of element 11's {@code 200} row, never a second event.
     *
     * @param productNumber the product being packed
     * @param employeeId    the caller, who must be the assignee (element 16: only he packs)
     */
    void pack(String productNumber, String employeeId) {
        ensureWorkIsInHand("packing", employeeId);
        OrderPosition position = positionOf(productNumber);
        ensurePackable(position);
        if (position.state() == PositionState.PICKED) {
            positions.set(positions.indexOf(position), position.packed());
            state = state.afterPacking(positionsAllPacked());
            events.add(new DomainEvent.PositionPacked(processingId, productNumber));
        }
    }

    /**
     * Declares the order done: the local mark and the collection request are one decision with two effects
     * (element 08). The aggregate owns the first effect and answers the verdict, so the caller asks the carrier for the
     * second only when the decision granted — never by re-deriving the table's own condition.
     *
     * @param parcelId the registered parcel the carrier is asked to collect, or {@code null} when none exists
     * @return the verdict the decision answered, which the caller applies
     */
    ReadyForDeliveryDecision.Verdict markReady(ParcelId parcelId) {
        boolean parcelRegistered = parcelId != null;
        ReadyForDeliveryDecision.Verdict verdict =
                ReadyForDeliveryDecision.decide(state, positionsAllPacked(), parcelRegistered);
        ensureReadinessIsGranted(verdict);
        if (verdict instanceof ReadyForDeliveryDecision.Verdict.Grant) {
            this.parcelId = parcelId;
            state = ProcessingState.READY_FOR_DELIVERY;
            events.add(new DomainEvent.ReadyForDeliveryConfirmed(processingId, parcelId));
        }
        return verdict;
    }

    /**
     * Records a warehouse reality gap and reflects its consequence on the order (elements 04 and 09). One issue holds
     * the whole order; a further issue on an already held order is recorded without a second hold.
     *
     * @param issue the issue as the employee reported it
     */
    void reportIssue(FulfillmentIssue issue) {
        Objects.requireNonNull(issue, "issue");
        HoldDecision.Verdict verdict =
                HoldDecision.decide(HoldDecision.Command.REPORT_ISSUE, state, openIssues().size());
        ensureIssueIsAccepted(verdict);
        ensureIssueIsReportedHere(issue);
        issues.add(issue);
        events.add(new DomainEvent.IssueReported(issue.issueId(), processingId, issue.kind()));
        placeHoldWhenGranted(verdict, issue.reportedAt());
    }

    /**
     * Lifts the hold once the named issues are closed and none is left open, returning the order to the state it was
     * held from (element 09 H5). A resume of an order that is not on hold changes nothing.
     *
     * @param closedIssueIds the issues the command vouches for; each must belong to this processing
     * @param at             the moment the resume is recorded, written to every named issue
     */
    void resume(List<IssueId> closedIssueIds, Instant at) {
        Objects.requireNonNull(closedIssueIds, "closedIssueIds");
        ensureIssuesAreKnown(closedIssueIds);
        List<IssueId> remaining = openIssuesAfter(closedIssueIds);
        HoldDecision.Verdict verdict = HoldDecision.decide(HoldDecision.Command.RESUME_HOLD, state, remaining.size());
        ensureHoldIsLiftable(verdict, remaining);
        resumeWhenGranted(verdict, closedIssueIds, at);
    }

    /**
     * Applies the carrier's delivery state to the order: only the last leg — the customer picked the parcel — moves it
     * to {@code delivered}; every earlier leg lives on the parcel alone. The leg is the order's own input: the status poll
     * of the parcel slice reports it as {@code delivery state notification} and this aggregate consumes it (element 02).
     *
     * @param leg the delivery leg the carrier reported
     */
    void applyDeliveryState(Leg leg) {
        Objects.requireNonNull(leg, "leg");
        if (leg == Leg.L6 && state == ProcessingState.READY_FOR_DELIVERY) {
            state = ProcessingState.DELIVERED;
            events.add(new DomainEvent.OrderDelivered(processingId));
        }
    }

    /**
     * Reads the processing as an immutable snapshot.
     *
     * @return the state as the read side serves it
     */
    OrderProcessing toSnapshot() {
        return new OrderProcessing(processingId, order, order.orderHash(), assignee, state, heldState,
                List.copyOf(positions), parcelId, List.copyOf(issues));
    }

    private static List<OrderPosition> positionsOf(Order order) {
        return order.lines().stream()
                .map(line -> new OrderPosition(line.productNumber(), line.name(), line.quantity(), PositionState.PENDING))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void ensureWorkIsInHand(String action, String employeeId) {
        if (state == ProcessingState.PENDING) {
            throw new FulfillmentException(FulfillmentException.Error.WORK_NOT_STARTED, processingId, action);
        }
        if (state == ProcessingState.ON_HOLD) {
            throw new FulfillmentException(FulfillmentException.Error.WORK_IS_ON_HOLD, processingId, action);
        }
        if (!Objects.equals(assignee, employeeId)) {
            throw new FulfillmentException(
                    FulfillmentException.Error.ACTOR_NOT_ALLOWED, employeeId, processingId, action);
        }
    }

    private void ensureClaimIsAccepted(AssignmentGuard.Verdict verdict) {
        if (verdict instanceof AssignmentGuard.Verdict.Refuse refuse) {
            throw new FulfillmentException(
                    FulfillmentException.Error.ASSIGNMENT_REFUSED, processingId, refuse.reason());
        }
    }

    private void ensureReadinessIsGranted(ReadyForDeliveryDecision.Verdict verdict) {
        if (verdict instanceof ReadyForDeliveryDecision.Verdict.Refuse refuse) {
            throw new FulfillmentException(FulfillmentException.Error.READINESS_REFUSED,
                    processingId, List.of(refuse.reason().wireName()));
        }
    }

    private void ensureIssueIsAccepted(HoldDecision.Verdict verdict) {
        if (verdict instanceof HoldDecision.Verdict.Refuse refuse) {
            throw new FulfillmentException(FulfillmentException.Error.HOLD_REFUSED, processingId, refuse.reason());
        }
    }

    private void ensureHoldIsLiftable(HoldDecision.Verdict verdict, List<IssueId> remaining) {
        if (verdict instanceof HoldDecision.Verdict.Refuse) {
            throw new FulfillmentException(FulfillmentException.Error.ISSUES_REMAIN, processingId, remaining);
        }
    }

    private void ensurePackable(OrderPosition position) {
        if (position.state() == PositionState.PENDING) {
            throw new FulfillmentException(FulfillmentException.Error.POSITION_NOT_PICKED,
                    position.productNumber(), position.state());
        }
    }

    private void ensureIssueIsReportedHere(FulfillmentIssue issue) {
        if (!processingId.equals(issue.processingId())) {
            throw new FulfillmentException(FulfillmentException.Error.ISSUE_UNKNOWN, issue.issueId(), processingId);
        }
        if (!Objects.equals(assignee, issue.reportedBy())) {
            throw new FulfillmentException(
                    FulfillmentException.Error.ISSUE_NOT_REPORTED_BY_ASSIGNEE, issue.reportedBy(), processingId);
        }
    }

    private void ensureIssuesAreKnown(List<IssueId> closedIssueIds) {
        for (IssueId issueId : closedIssueIds) {
            boolean known = issues.stream().anyMatch(issue -> issue.issueId().equals(issueId));
            if (!known) {
                throw new FulfillmentException(FulfillmentException.Error.ISSUE_UNKNOWN, issueId, processingId);
            }
        }
    }

    private void placeHoldWhenGranted(HoldDecision.Verdict verdict, Instant since) {
        if (verdict instanceof HoldDecision.Verdict.Grant) {
            heldState = state;
            state = ProcessingState.ON_HOLD;
            events.add(new DomainEvent.HoldPlaced(processingId, since));
        }
    }

    private void resumeWhenGranted(HoldDecision.Verdict verdict, List<IssueId> closedIssueIds, Instant at) {
        if (verdict instanceof HoldDecision.Verdict.Grant) {
            issues.replaceAll(issue -> closedIssueIds.contains(issue.issueId()) ? issue.close(at) : issue);
            state = heldState;
            heldState = null;
            events.add(new DomainEvent.OrderResumed(processingId, state));
        }
    }

    private OrderPosition positionOf(String productNumber) {
        return positions.stream()
                .filter(position -> position.productNumber().equals(productNumber))
                .findFirst()
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.POSITION_UNKNOWN, processingId, productNumber));
    }

    private boolean positionsAllPacked() {
        return positions.stream().allMatch(position -> position.state() == PositionState.PACKED);
    }

    private List<IssueId> openIssues() {
        return openIssuesAfter(List.of());
    }

    private List<IssueId> openIssuesAfter(List<IssueId> closedIssueIds) {
        return issues.stream()
                .filter(issue -> issue.isOpen() && !closedIssueIds.contains(issue.issueId()))
                .map(FulfillmentIssue::issueId)
                .toList();
    }
}
