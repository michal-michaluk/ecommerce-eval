package shop.fulfillment.processing;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.response.DefaultResponseCreator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import shop.fulfillment.kernel.LocationCode;

import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The vendor payloads this adapter is asserted against, recorded rather than fetched: one page of stock items, one page of
 * locations, a page ordered by the last update, and a burst that does not fit in one page.
 */
public class InvenTreePayloadFixture {

    public static final String URL = "http://inventree.test";
    public static final String TOKEN = "t0ken";
    public static final String PRODUCT = "75423";
    public static final String ORCHID = "99881";
    public static final int PAGE = 10;
    public static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final LocationCode SHELF = new LocationCode("M-02-01");

    public static final String LOCATIONS = """
            {"count": 1, "results": [{"pk": 3, "name": "M-02-01", "pathstring": "WH/M-02-01"}]}""";

    public static final String STOCK_PAGE = """
            {"count": 2, "results": [
              {"pk": 41, "part": 75423, "quantity": 4, "allocated": 0, "location": 3,
               "updated": "2026-11-19T09:00:00Z"},
              {"pk": 42, "part": 75423, "quantity": 4, "allocated": 2, "location": 3,
               "updated": "2026-11-19T09:00:00Z"}]}""";

    public static final String ORCHID_PAGE = """
            {"count": 1, "results": [
              {"pk": 51, "part": 99881, "quantity": 5, "allocated": 0, "location": 3,
               "updated": "2026-11-19T09:00:00Z"}]}""";

    public static final String FRACTIONAL_PAGE = """
            {"count": 1, "results": [{"pk": 41, "part": 75423, "quantity": 4.5, "allocated": 0, "location": 3,
             "updated": "2026-11-19T09:00:00Z"}]}""";

    public static final String CHANGED_PAGE = """
            {"count": 3, "results": [
              {"pk": 41, "part": 75423, "quantity": 4, "allocated": 0, "location": 3,
               "updated": "2026-11-19T11:00:00Z"},
              {"pk": 42, "part": 75423, "quantity": 3, "allocated": 1, "location": 3,
               "updated": "2026-11-19T10:30:00Z"},
              {"pk": 43, "part": 75423, "quantity": 9, "allocated": 0, "location": 3,
               "updated": "2026-11-19T09:00:00Z"}]}""";

    public static final String FIRST_PAGE = """
            {"count": 4, "results": [
              {"pk": 41, "part": 75423, "quantity": 4, "allocated": 0, "location": 3,
               "updated": "2026-11-19T11:00:00Z"},
              {"pk": 42, "part": 75423, "quantity": 3, "allocated": 1, "location": 3,
               "updated": "2026-11-19T10:30:00Z"}]}""";

    public static final String SECOND_PAGE = """
            {"count": 4, "results": [
              {"pk": 43, "part": 75423, "quantity": 1, "allocated": 0, "location": 3,
               "updated": "2026-11-19T10:15:00Z"},
              {"pk": 44, "part": 75423, "quantity": 9, "allocated": 0, "location": 3,
               "updated": "2026-11-19T09:00:00Z"}]}""";

    private InvenTreePayloadFixture() {
    }

    public static InvenTreeWarehouseClient.Settings settings() {
        return settings(PAGE);
    }

    public static InvenTreeWarehouseClient.Settings settings(int pageSize) {
        return new InvenTreeWarehouseClient.Settings(URL, TOKEN, pageSize);
    }

    public static DefaultResponseCreator page(String json) {
        return withSuccess(json, MediaType.APPLICATION_JSON);
    }
}
