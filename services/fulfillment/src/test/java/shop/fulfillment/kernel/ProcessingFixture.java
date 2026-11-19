package shop.fulfillment.kernel;

import java.time.Instant;

/**
 * The processing values the tests work with: one position per order line, an issue as an employee reports it, and the two
 * identities a processing and an issue are addressed by.
 */
public class ProcessingFixture {

    public static final OrderProcessingId PROCESSING_ID = new OrderProcessingId("OP-2026-0001");
    public static final OrderProcessingId OTHER_PROCESSING_ID = new OrderProcessingId("OP-2026-0002");
    public static final IssueId ISSUE_ID = new IssueId("ISS-2026-0031");
    public static final ParcelId PARCEL_ID = new ParcelId("PCL-2026-0001");
    public static final Instant REPORTED_AT = Instant.parse("2026-11-19T11:00:00Z");
    public static final String EMPLOYEE = "employee-42";
    public static final String OTHER_EMPLOYEE = "employee-17";

    private ProcessingFixture() {
    }

    public static OrderPosition aPosition() {        return aPositionAs(OrderFixture.PRODUCT_NUMBER, OrderFixture.PRODUCT_NAME, 1, PositionState.PENDING);
    }

    public static OrderPosition aPosition(String productNumber, int quantity) {
        return aPositionAs(productNumber, "LEGO " + productNumber, quantity, PositionState.PENDING);
    }

    public static OrderPosition aPositionAs(String productNumber, String name, int quantity, PositionState state) {
        return new OrderPosition(productNumber, name, quantity, state);
    }

    public static OrderPosition aPickedPosition() {
        return aPosition().picked();
    }

    public static FulfillmentIssue anIssue() {
        return issueOn(PROCESSING_ID, ISSUE_ID);
    }

    public static FulfillmentIssue anIssueReportedBy(String employeeId) {
        return issue(ISSUE_ID, PROCESSING_ID, employeeId);
    }

    public static FulfillmentIssue issueOn(OrderProcessingId processingId, IssueId issueId) {
        return issue(issueId, processingId, EMPLOYEE);
    }

    /** The carrier's leg as the parcel slice raises it: the order's own input from a neighbour (element 02). */
    public static DomainEvent.DeliveryStateNotified aLegNotified(ParcelId parcelId, Leg leg) {
        return new DomainEvent.DeliveryStateNotified(parcelId, leg, leg.name());
    }

    public static FulfillmentIssue issue(IssueId issueId, OrderProcessingId processingId, String reportedBy) {
        return FulfillmentIssue.builder()
                .issueId(issueId)
                .processingId(processingId)
                .kind(IssueKinds.LOST)
                .productNumber(OrderFixture.PRODUCT_NUMBER)
                .reportedBy(reportedBy)
                .reportedAt(REPORTED_AT)
                .build();
    }
}
