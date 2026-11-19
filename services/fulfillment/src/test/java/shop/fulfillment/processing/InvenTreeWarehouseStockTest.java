package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClientException;

import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.StockChange;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * What the adapter reads: the stock of one product per location with free and allocated units as two sums, the settings a
 * deployment configured, and the stock-change read that pages back to our own watermark.
 */
class InvenTreeWarehouseStockTest extends InvenTreeWarehouseTest {

    @Test
    void translatesTheStockOfOneProductPerLocation() {
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.STOCK_PAGE));

        List<StockAtLocation> stock = client.stockAt(new ProductId(InvenTreePayloadFixture.PRODUCT));

        assertThat(stock).singleElement().satisfies(place -> {
            assertThat(place.code().value()).isEqualTo("M-02-01");
            assertThat(place.available()).isEqualTo(6);
            assertThat(place.reserved()).isEqualTo(2);
        });
        warehouse.verify();
    }

    @Test
    void readsItsSettingsFromConfiguration() {
        MockEnvironment configuration = new MockEnvironment()
                .withProperty("fulfillment.warehouse.url", InvenTreePayloadFixture.URL)
                .withProperty("fulfillment.warehouse.token", InvenTreePayloadFixture.TOKEN);

        InvenTreeWarehouseClient.Settings settings = InvenTreeWarehouseClient.Settings.from(configuration);

        assertThat(settings.url()).isEqualTo(InvenTreePayloadFixture.URL);
        assertThat(settings.authorization()).isEqualTo("Token " + InvenTreePayloadFixture.TOKEN);
        assertThat(settings.pageSize()).isEqualTo(100);
    }

    @Test
    void refusesAFractionalQuantityInsteadOfRoundingIt() {
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.FRACTIONAL_PAGE));

        assertThatThrownBy(() -> client.stockAt(new ProductId(InvenTreePayloadFixture.PRODUCT)))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_QUANTITY_FRACTIONAL));
    }

    @Test
    void refusesAProductNumberTheWarehouseCannotAddress() {
        assertThatThrownBy(() -> client.stockAt(new ProductId("LEGO-X-WING")))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PRODUCT_NUMBER_MALFORMED));
    }

    @Test
    void readsWhatChangedAfterTheCursorAndAnswersTheNewWatermark() {
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&ordering=-updated"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.CHANGED_PAGE));
        locations();

        StockChangePage changed = client.stockChanges(new StockCursor("2026-11-19T10:00:00Z"));

        assertThat(changed.changes()).extracting(StockChange::available).containsExactly(4, 2);
        assertThat(changed.changes()).extracting(change -> change.location().value()).containsOnly("M-02-01");
        assertThat(changed.nextCursor().value()).isEqualTo("2026-11-19T10:30:00Z");
        warehouse.verify();
    }

    @Test
    void followsThePagesOfABurstLargerThanOnePage() {
        client = WarehouseAdapterFixture.invenTree(builder.build(), InvenTreePayloadFixture.settings(2), state,
                InvenTreePayloadFixture.CLOCK);
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=2&ordering=-updated"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.FIRST_PAGE));
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=2&ordering=-updated&offset=2"))
                .andRespond(InvenTreePayloadFixture.page(InvenTreePayloadFixture.SECOND_PAGE));
        locations(2);

        StockChangePage changed = client.stockChanges(new StockCursor("2026-11-19T10:00:00Z"));

        assertThat(changed.changes()).extracting(StockChange::available).containsExactly(4, 2, 1);
        assertThat(changed.nextCursor().value()).isEqualTo("2026-11-19T10:15:00Z");
        warehouse.verify();
    }

    @Test
    void surfacesAWarehouseFailureInsteadOfInventingOne() {
        locations();
        warehouse.expect(requestTo(InvenTreePayloadFixture.URL + "/api/stock/?limit=10&part=75423"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.stockAt(new ProductId(InvenTreePayloadFixture.PRODUCT)))
                .isInstanceOf(RestClientException.class);
    }
}
