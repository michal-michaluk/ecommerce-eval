package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import shop.fulfillment.HandoverJson;
import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.RecordedEvents;
import shop.fulfillment.kernel.DomainEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Element 12's route over the real chain: {@code 201} for the delivery that opened the processing, {@code 200} with
 * {@code duplicate} for a repeated order, {@code 409 ORDER_HASH_CONFLICT} for another order sent under a used hash,
 * {@code 422 ORDER_INCOMPLETE} naming the payload part that has to be fixed — for a missing component and for a part that is
 * there but unreadable — and the scope matrix around all of them.
 */
@IntegrationTest
class OrderIntakeControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    RecordedEvents emitted;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void opensTheProcessingWith201AndItsPendingState() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.complete());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(HttpFixture.json(response, "$.processingId")).asString().matches("OP-\\d{4}-\\d{4}");
        assertThat(HttpFixture.json(response, "$.state")).isEqualTo("pending");
    }

    @Test
    void answersARepeatedOrderWith200AndTheSameProcessing() throws Exception {
        String processingId = (String) HttpFixture.json(accept(HandoverJson.complete()), "$.processingId");

        MockHttpServletResponse duplicate = accept(HandoverJson.complete());

        assertThat(duplicate.getStatus()).isEqualTo(200);
        assertThat(HttpFixture.json(duplicate, "$.processingId")).isEqualTo(processingId);
        assertThat(HttpFixture.json(duplicate, "$.duplicate")).isEqualTo(true);
    }

    @Test
    void announcesTheProcessingOnceForARepeatedHandover() throws Exception {
        accept(HandoverJson.complete());

        accept(HandoverJson.complete());

        assertThat(emitted.of(DomainEvent.OrderProcessingStarted.class)).hasSize(1);
    }

    @Test
    void refusesADifferentOrderSentUnderAUsedOrderHash() throws Exception {
        accept(HandoverJson.complete());

        MockHttpServletResponse conflict = accept(HandoverJson.sameHashOtherQuantity());

        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(HttpFixture.json(conflict, "$.error")).isEqualTo("ORDER_HASH_CONFLICT");
    }

    @Test
    void refusesAnIncompleteOrderWith422AndTheMissingComponents() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.incomplete());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("ORDER_INCOMPLETE");
        assertThat(HttpFixture.json(response, "$.missing")).asList().contains("lines");
    }

    @Test
    void refusesAPickupPointWithoutItsPointId() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.pickupPointWithoutPointId());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("ORDER_INCOMPLETE");
        assertThat(HttpFixture.json(response, "$.missing")).asList().contains("delivery.location.pointId");
    }

    @Test
    void refusesALineWhoseQuantityIsNotPositive() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.lineWithoutQuantity());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(HttpFixture.json(response, "$.missing")).asList().contains("lines.quantity");
    }

    @Test
    void refusesALineWithoutAProductNumber() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.lineWithoutProductNumber());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(HttpFixture.json(response, "$.missing")).asList().contains("lines.productNumber");
    }

    @Test
    void refusesTwoLinesOfTheSameProduct() throws Exception {
        MockHttpServletResponse response = accept(HandoverJson.duplicatedLine());

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(HttpFixture.json(response, "$.error")).isEqualTo("ORDER_INCOMPLETE");
        assertThat(HttpFixture.json(response, "$.missing")).asList().contains("lines.productNumber");
    }

    @Test
    void refusesAHandoverWithoutAToken() throws Exception {
        assertThat(HttpFixture.anonymous(mvc, post("/orders").contentType(APPLICATION_JSON)
                .content(HandoverJson.complete())).getStatus()).isEqualTo(401);
    }

    @Test
    void refusesAHandoverFromAnyOtherScope() throws Exception {
        assertThat(HttpFixture.as(HttpFixture.EMPLOYEE_SCOPE, HttpFixture.EMPLOYEE, mvc,
                post("/orders").contentType(APPLICATION_JSON).content(HandoverJson.complete())).getStatus())
                .isEqualTo(403);
    }

    @Test
    void refusesAHandoverFromTheCustomerScope() throws Exception {
        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc,
                post("/orders").contentType(APPLICATION_JSON).content(HandoverJson.complete())).getStatus())
                .isEqualTo(403);
    }

    @Test
    void closesTheOrdersListToTheShoppingScope() throws Exception {
        assertThat(HttpFixture.as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, mvc, get("/orders")).getStatus())
                .isEqualTo(403);
    }

    @Test
    void closesTheOrdersListToACustomer() throws Exception {
        assertThat(HttpFixture.as(HttpFixture.CUSTOMER_SCOPE, HttpFixture.CUSTOMER, mvc, get("/orders")).getStatus())
                .isEqualTo(403);
    }

    private MockHttpServletResponse accept(String handover) throws Exception {
        MockHttpServletRequestBuilder request = post("/orders").contentType(APPLICATION_JSON).content(handover);
        return HttpFixture.as(HttpFixture.SHOPPING_SCOPE, HttpFixture.SHOPPING, mvc, request);
    }
}
