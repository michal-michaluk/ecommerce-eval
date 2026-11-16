package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reads fragment is the only place the read models' schema exists: Hibernate never owns it ({@code ddl-auto: none}), so a table
 * that is missing here is missing everywhere. The integration profile recreates the schema on every start, so this runs on a clean
 * database.
 */
@IntegrationTest
class ReadsMigrationTest {

    private static final List<String> TABLES = List.of("offer_draft_reads", "pending_reviews_reads", "pricing_reads");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsEveryReadModelTable() {
        assertThat(query("select table_name from information_schema.tables where table_schema = 'public'"))
                .containsAll(TABLES);
    }

    @Test
    void appliesEveryReadsChangeSet() {
        assertThat(query("select id from databasechangelog where id like '0006-%'"))
                .containsExactlyInAnyOrder("0006-reads-offer-draft", "0006-reads-pending-reviews",
                        "0006-reads-pricing");
    }

    /** 15's list filters on the state, sorts by the request instant and drops a reviewer's own requests in the query. */
    @Test
    void indexesTheColumnsThePendingListFiltersAndSortsOn() {
        assertThat(query("select indexname from pg_indexes where tablename = 'pending_reviews_reads'"))
                .contains("pending_reviews_reads_awaiting_idx", "pending_reviews_reads_requested_by_idx");
    }

    @Test
    void storesTheDraftAndThePriceSeriesAsDocumentsOutsideTheColumnsQueriesUse() {
        assertThat(columnType("offer_draft_reads", "content")).isEqualTo("jsonb");
        assertThat(columnType("pricing_reads", "prices")).isEqualTo("jsonb");
        assertThat(columnType("pricing_reads", "discounts")).isEqualTo("jsonb");
        assertThat(query("select column_name from information_schema.columns where table_name = 'offer_draft_reads'"))
                .contains("offer_id", "state", "availability_start");
    }

    private String columnType(String table, String column) {
        return jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_name = ? and column_name = ?", String.class, table, column);
    }

    private List<String> query(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
