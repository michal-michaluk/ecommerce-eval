package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import shop.fulfillment.FakePorts;
import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.RecordedEvents;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.OrderFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Elements 04, 09 and 11 over the real chain: reporting an issue holds the whole order once, a second issue joins the hold it
 * needs no second one, only a resume that leaves nothing open lifts it, and the refusals answer the codes element 11 gives
 * them — {@code NOT_STARTED}, {@code PAST_HANDOVER}, {@code ISSUES_REMAIN} with its count, and the {@code 404} of an issue the
 * order does not account for.
 */
@IntegrationTest
@Import(FakePorts.class)
class IssuesControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;
    @Autowired
    ProcessingService work;
    @Autowired
    RecordedEvents emitted;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void holdsTheWholeOrderAndAnswersTheIssueItWrote() throws Exception {
        String processingId = pickingProcessing();

        MockHttpServletResponse response = report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}");

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(HttpFixture.json(response, "$.issueId")).asString().startsWith("ISS-");
        assertThat(HttpFixture.json(response, "$.orderState")).isEqualTo("onHold");
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("onHold");
        assertThat(HttpFixture.json(response, "$.openIssues")).isEqualTo(1);
        assertThat(HttpFixture.json(response, "$.issue.kind")).isEqualTo("LOST");
        assertThat(HttpFixture.json(response, "$.issue.reportedBy")).isEqualTo(HttpFixture.EMPLOYEE);
    }

    @Test
    void recordsASecondIssueWithoutASecondHold() throws Exception {
        String processingId = pickingProcessing();
        report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}");

        MockHttpServletResponse second = report(processingId,
                "{\"kind\": \"WHATEVER_THE_WAREHOUSE_SAYS\", \"productNumber\": \"75423\"}");

        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(HttpFixture.json(second, "$.openIssues")).isEqualTo(2);
        assertThat(emitted.of(DomainEvent.HoldPlaced.class)).hasSize(1);
        assertThat(emitted.of(DomainEvent.IssueReported.class)).hasSize(2);
    }

    @Test
    void keepsTheUnknownKindVerbatim() throws Exception {
        String processingId = pickingProcessing();

        report(processingId, "{\"kind\": \"DAMAGED_IN_TRANSIT\", \"productNumber\": \"75423\"}");

        assertThat(emitted.of(DomainEvent.IssueReported.class)).singleElement()
                .satisfies(event -> assertThat(event.kind()).isEqualTo("DAMAGED_IN_TRANSIT"));
    }

    @Test
    void liftsTheHoldOnceTheResumeNamesEverythingThatWasOpen() throws Exception {
        String processingId = pickingProcessing();
        String issueId = issueIdOf(report(processingId,
                "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));

        MockHttpServletResponse resumed = resume(processingId, HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER,
                closed(issueId));

        assertThat(resumed.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(resumed, "$.state")).isEqualTo("picking");
        assertThat(HttpFixture.json(resumed, "$.openIssues")).isEqualTo(0);
        assertThat(HttpFixture.json(resumed, "$.issue")).isNull();
        assertThat(HttpFixture.json(resumed, "$.closedIssueIds[0]")).isEqualTo(issueId);
        assertThat(emitted.of(DomainEvent.OrderResumed.class)).hasSize(1);
    }

    @Test
    void refusesAResumeThatLeavesAnIssueOpenAndCountsIt() throws Exception {
        String processingId = pickingProcessing();
        String first = issueIdOf(report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));
        report(processingId, "{\"kind\": \"STOLEN\", \"productNumber\": \"75423\"}");

        MockHttpServletResponse refused = resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                closed(first));

        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(refused, "$.error")).isEqualTo("ISSUES_REMAIN");
        assertThat(HttpFixture.json(refused, "$.openIssues")).isEqualTo(1);
        assertThat(emitted.of(DomainEvent.OrderResumed.class)).isEmpty();
    }

    @Test
    void refusesAnIssueTheOrderDoesNotAccountFor() throws Exception {
        String processingId = pickingProcessing();

        MockHttpServletResponse unknown = resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                closed("ISS-2026-9999"));
        MockHttpServletResponse malformed = resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                closed("ISS-99"));

        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(unknown, "$.error")).isEqualTo("ISSUE_UNKNOWN");
        assertThat(malformed.getStatus()).isEqualTo(404);
        assertThat(HttpFixture.json(malformed, "$.error")).isEqualTo("ISSUE_ID_MALFORMED");
    }

    @Test
    void refusesAnIssueTheOrderDoesNotHaveAndOneOfAnotherOrder() throws Exception {
        String processingId = pickingProcessing();
        String otherProcessing = pickingProcessing("sha256:7d2c5f30");
        String otherIssue = issueIdOf(report(otherProcessing, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));

        assertThat(resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, closed(otherIssue)).getStatus())
                .isEqualTo(404);
    }

    @Test
    void answersAResumeOfAnOrderThatIsNotHeldWithTheStateItAlreadyHad() throws Exception {
        String processingId = pickingProcessing();

        MockHttpServletResponse resumed = resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                "{\"closedIssueIds\": []}");

        assertThat(resumed.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(resumed, "$.state")).isEqualTo("picking");
        assertThat(HttpFixture.json(resumed, "$.closedIssueIds")).asList().isEmpty();
    }

    @Test
    void answersAReplayedResumeNamingAClosedIssueWithNothingClosed() throws Exception {
        String processingId = pickingProcessing();
        String issueId = issueIdOf(report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));
        resume(processingId, HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER, closed(issueId));

        MockHttpServletResponse replayed = resume(processingId, HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE,
                closed(issueId));

        assertThat(replayed.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(replayed, "$.state")).isEqualTo("picking");
        assertThat(HttpFixture.json(replayed, "$.closedIssueIds")).asList().isEmpty();
    }

    @Test
    void refusesAnIssueBeforePickingStarted() throws Exception {
        String processingId = assignedProcessing();

        MockHttpServletResponse response = report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("NOT_STARTED");
        assertThat(emitted.of(DomainEvent.IssueReported.class)).isEmpty();
    }

    @Test
    void refusesAnIssueOnceTheOrderLeftTheWarehouse() throws Exception {
        String processingId = readyProcessing();

        MockHttpServletResponse response = report(processingId, "{\"kind\": \"LOST\", \"productNumber\": \"75423\"}");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("PAST_HANDOVER");
    }

    @Test
    void refusesAnIssueReportedByAnEmployeeWhoDoesNotHoldTheOrder() throws Exception {
        String processingId = pickingProcessing();

        MockHttpServletResponse response = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.OTHER_EMPLOYEE, mvc,
                post("/processing/" + processingId + "/issues")
                        .contentType(APPLICATION_JSON)
                        .content("{\"kind\": \"LOST\", \"productNumber\": \"75423\"}"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("ACTOR_NOT_ALLOWED");
    }

    @Test
    void keepsTheHoldCommandsOutOfTheScopesThatMayNotIssueThem() throws Exception {
        String processingId = pickingProcessing();

        assertThat(HttpFixture.as(HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER, mvc,
                post("/processing/" + processingId + "/issues")
                        .contentType(APPLICATION_JSON)
                        .content("{\"kind\": \"LOST\", \"productNumber\": \"75423\"}")).getStatus())
                .isEqualTo(403);
        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                post("/processing/" + processingId + "/hold/resume")
                        .contentType(APPLICATION_JSON)
                        .content("{\"closedIssueIds\": []}")).getStatus()).isEqualTo(403);
        assertThat(HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/OP-2026-9999/hold/resume")
                        .contentType(APPLICATION_JSON)
                        .content("{\"closedIssueIds\": []}")).getStatus()).isEqualTo(404);
    }

    @Test
    void refusesAReportWithoutAKindAndWithoutAProduct() throws Exception {
        String processingId = pickingProcessing();

        assertThat(report(processingId, "{\"kind\": \"  \", \"productNumber\": \"75423\"}").getStatus()).isEqualTo(400);
        assertThat(report(processingId, "{\"kind\": \"LOST\"}").getStatus()).isEqualTo(400);
    }

    @Test
    void stopsEveryCommandOfAHeldOrderAndResumesTheOneItWasHeldFrom() throws Exception {
        String processingId = pickingProcessing();
        String issueId = issueIdOf(report(processingId,
                "{\"kind\": \"LOST\", \"productNumber\": \"99881\"}"));

        MockHttpServletResponse scanWhileHeld = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/positions/99881")
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirmation\": \"picked\"}"));
        MockHttpServletResponse resume = resume(processingId, HttpFixture.RESOLVER_SCOPE, HttpFixture.RESOLVER,
                closed(issueId));
        MockHttpServletResponse scanAfterResume = HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/positions/99881")
                        .contentType(APPLICATION_JSON)
                        .content("{\"confirmation\": \"picked\"}"));

        assertThat(scanWhileHeld.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(scanWhileHeld, "$.error")).isEqualTo("ON_HOLD");
        assertThat(resume.getStatus()).isEqualTo(200);
        assertThat(scanAfterResume.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(scanAfterResume, "$.state")).isEqualTo("picking");
    }

    private MockHttpServletResponse report(String processingId, String body) throws Exception {
        return HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/processing/" + processingId + "/issues").contentType(APPLICATION_JSON).content(body));
    }

    private MockHttpServletResponse resume(String processingId, String scope, String caller, String body)
            throws Exception {
        return HttpFixture.as(scope, caller, mvc,
                post("/processing/" + processingId + "/hold/resume").contentType(APPLICATION_JSON).content(body));
    }

    private static String closed(String... issueIds) {
        return "{\"closedIssueIds\": [\"" + String.join("\",\"", issueIds) + "\"]}";
    }

    private static String issueIdOf(MockHttpServletResponse response) throws Exception {
        return String.valueOf(HttpFixture.json(response, "$.issueId"));
    }

    private String assignedProcessing() {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);
        return processingId;
    }

    /** The order has two lines, so one scan leaves a position open — the state an issue is reported in. */
    private String pickingProcessing() {
        return pickingProcessing(OrderFixture.ORDER_HASH);
    }

    private String pickingProcessing(String orderHash) {
        String processingId = orders
                .accept(OrderHandoverFixture.aTwoLineHandover(orderHash)).processing().processingId().value();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);
        return processingId;
    }

    private String readyProcessing() {
        String processingId = assignedProcessing();
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);
        work.pack(processingId, List.of("75423"), HttpFixture.EMPLOYEE);
        work.markReady(processingId);
        return processingId;
    }
}
