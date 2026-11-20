package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The one stubbed HTTP layer the adapter tests share: a client built against recorded payloads, the two stores of the state
 * this boundary owns, and the two requests every translation starts with.
 */
abstract class InvenTreeWarehouseTest {

    final FakeWarehouseState state = new FakeWarehouseState();
    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer warehouse = MockRestServiceServer.bindTo(builder).build();
    InvenTreeWarehouseClient client;

    @BeforeEach
    void oneClient() {
        client = WarehouseAdapterFixture.invenTree(builder.build(), InvenTreePayloadFixture.settings(), state,
                InvenTreePayloadFixture.CLOCK);
    }

    /** The location list every request that names a location reads first, with the token the deployment configured. */
    void locations() {
        locations(InvenTreePayloadFixture.PAGE);
    }

    void locations(int pageSize) {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/location/?limit=" + pageSize))
                .andExpect(header("Authorization", "Token " + InvenTreePayloadFixture.TOKEN))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.LOCATIONS));
    }

    void allowReleaseOf(int allocationPk) {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/order/transfer-order-allocation/"
                + allocationPk + "/"))
                .andRespond(withSuccess());
    }
}
