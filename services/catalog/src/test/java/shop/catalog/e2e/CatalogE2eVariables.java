package shop.catalog.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The variables the hurl suite reads: one token per scope of 12 and — D35 — every date the requests carry.
 *
 * <p>No request names a fixed date. A published offer proves visibility only relative to the business date, so the suite asks
 * for an {@code availabilityStart} a month ahead and one a month behind, and the same for the pricing ranges, all computed from
 * the run's own clock. The suite therefore asserts the same things on any day it is run, and the file can be replayed by hand
 * with any dates a reader chooses.
 */
final class CatalogE2eVariables {

    static final int APP_PORT = 18090;

    private static final int AVAILABILITY_DAYS = 30;
    private static final Duration PRICE_STARTS_IN = Duration.ofDays(30);
    private static final Duration PRICE_ENDS_IN = Duration.ofDays(60);

    private CatalogE2eVariables() {
    }

    static Map<String, String> current() {
        LocalDate today = LocalDate.now(Clock.systemUTC());
        Instant now = Instant.now(Clock.systemUTC()).truncatedTo(ChronoUnit.SECONDS);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("BASE_URL", "http://localhost:" + APP_PORT);
        variables.put("availability_future", today.plusDays(AVAILABILITY_DAYS).toString());
        variables.put("availability_past", today.minusDays(AVAILABILITY_DAYS).toString());
        variables.put("price_from_future", now.plus(PRICE_STARTS_IN).toString());
        variables.put("price_until_future", now.plus(PRICE_ENDS_IN).toString());
        variables.put("author_token", CatalogRealmFixture.token("author"));
        variables.put("reviewer_token", CatalogRealmFixture.token("reviewer"));
        variables.put("photographer_token", CatalogRealmFixture.token("photographer"));
        variables.put("sales_token", CatalogRealmFixture.token("sales"));
        return variables;
    }
}
