package shop.fulfillment.issues;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.IssueId;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Element 17's read model: one row per open issue, grouped by the order that holds it, fed by the events and by nothing else —
 * and rows leave only when the resume command closes them (register D8), never from this side.
 */
@IntegrationTest
class IssueDashboardProjectionTest {

    private static final Instant REPORTED_AT = Instant.parse("2026-11-19T11:00:00Z");

    @Autowired
    IssueDashboardProjection projection;

    @Test
    void showsAnIssueWithTheOrderThatHoldsIt() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(assigned(ProcessingFixture.PROCESSING_ID, ProcessingFixture.EMPLOYEE));

        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        assertThat(projection.findOpen()).singleElement().satisfies(row -> {
            assertThat(row.issueId()).isEqualTo(ProcessingFixture.ISSUE_ID.value());
            assertThat(row.kind()).isEqualTo("LOST");
            assertThat(row.product().productNumber()).isEqualTo(OrderFixture.PRODUCT_NUMBER);
            assertThat(row.product().name()).isEqualTo(OrderFixture.PRODUCT_NAME);
            assertThat(row.reportedBy()).isEqualTo(ProcessingFixture.EMPLOYEE);
            assertThat(row.reportedAt()).isEqualTo(REPORTED_AT);
            assertThat(row.orderState()).isEqualTo("onHold");
            assertThat(row.assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        });
    }

    @Test
    void showsAnUnknownKindVerbatimAndRejectsNoKind() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID,
                "SOMETHING_NOBODY_NAMED_YET"));

        assertThat(projection.findOpen()).singleElement()
                .satisfies(row -> assertThat(row.kind()).isEqualTo("SOMETHING_NOBODY_NAMED_YET"));
    }

    @Test
    void groupsTheRowsOfOneOrderTogetherAndPutsTheOldestGroupFirst() {
        projection.handle(started(ProcessingFixture.OTHER_PROCESSING_ID, OrderFixture.anOrder("sha256:9ab27e")));
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.OTHER_PROCESSING_ID));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        projection.handle(reported(new IssueId("ISS-2026-0002"), ProcessingFixture.PROCESSING_ID, "LOST"));
        projection.handle(reported(new IssueId("ISS-2026-0003"), ProcessingFixture.OTHER_PROCESSING_ID, "STOLEN"));
        projection.handle(reported(new IssueId("ISS-2026-0004"), ProcessingFixture.PROCESSING_ID, "STOLEN"));

        assertThat(projection.findOpen()).extracting(IssueDashboardEntry::issueId)
                .containsExactly("ISS-2026-0002", "ISS-2026-0004", "ISS-2026-0003");
    }

    @Test
    void leavesTheListWhenTheResumeClosesEveryIssueOfTheOrder() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));
        projection.handle(reported(new IssueId("ISS-2026-0002"), ProcessingFixture.PROCESSING_ID, "STOLEN"));

        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PICKING));

        assertThat(projection.findOpen()).isEmpty();
    }

    @Test
    void showsTheOrderInTheStateTheResumeRestored() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));

        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PICKING));

        projection.handle(reported(new IssueId("ISS-2026-0002"), ProcessingFixture.PROCESSING_ID, "LOST"));

        assertThat(projection.findOpen()).singleElement()
                .satisfies(row -> assertThat(row.orderState()).isEqualTo("picking"));
    }

    @Test
    void keepsAClosedIssueClosedWhenItsReportIsDeliveredAgain() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));
        projection.handle(resumed(ProcessingFixture.PROCESSING_ID, ProcessingState.PICKING));

        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));

        assertThat(projection.findOpen()).isEmpty();
    }

    @Test
    void keepsOneRowForOneIssueHoweverOftenItIsDelivered() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));

        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));

        assertThat(projection.findOpen()).hasSize(1);
    }

    @Test
    void showsNothingForAnIssueWhoseOrderNeverArrived() {
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));

        assertThat(projection.findOpen()).isEmpty();
    }

    @Test
    void carriesNoQuantityAndNoLocation() {
        projection.handle(started(ProcessingFixture.PROCESSING_ID, OrderFixture.anOrder()));
        projection.handle(held(ProcessingFixture.PROCESSING_ID));
        projection.handle(reported(ProcessingFixture.ISSUE_ID, ProcessingFixture.PROCESSING_ID, "LOST"));

        assertThat(projection.findOpen()).singleElement().satisfies(row -> assertThat(row.product())
                .isEqualTo(new IssueDashboardEntry.Product(OrderFixture.PRODUCT_NUMBER, OrderFixture.PRODUCT_NAME)));
    }

    private static DomainEvent.OrderProcessingStarted started(OrderProcessingId processingId,
                                                             shop.fulfillment.kernel.Order order) {
        return new DomainEvent.OrderProcessingStarted(processingId, order);
    }

    private static DomainEvent.EmployeeAssigned assigned(OrderProcessingId processingId, String assignee) {
        return new DomainEvent.EmployeeAssigned(processingId, assignee);
    }

    private static DomainEvent.IssueReported reported(IssueId issueId, OrderProcessingId processingId, String kind) {
        return ProcessingFixture.anIssueReported(issueId, processingId, kind);
    }

    private static DomainEvent.HoldPlaced held(OrderProcessingId processingId) {
        return new DomainEvent.HoldPlaced(processingId, REPORTED_AT);
    }

    private static DomainEvent.OrderResumed resumed(OrderProcessingId processingId, ProcessingState state) {
        return new DomainEvent.OrderResumed(processingId, state);
    }
}
