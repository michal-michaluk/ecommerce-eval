package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer fragment is the only place the offer schema exists: Hibernate never owns it ({@code ddl-auto: none}), so a table that
 * is missing here is missing everywhere. The integration profile recreates the schema on every start, so this runs on a clean
 * database.
 */
@IntegrationTest
class OfferMigrationTest {

    private static final List<String> TABLES =
            List.of("offer_document", "offer_version", "offer_version_media", "offer_history");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsEveryOfferTable() {
        assertThat(query("select table_name from information_schema.tables where table_schema = 'public'"))
                .containsAll(TABLES);
    }

    @Test
    void appliesEveryOfferChangeSet() {
        assertThat(query("select id from databasechangelog where id like '0003-%'"))
                .containsExactlyInAnyOrder("0003-offer-document", "0003-offer-version", "0003-offer-version-media",
                        "0003-offer-history");
    }

    @Test
    void enforcesTheIdentifiersOfEveryOfferWithAUniqueIndex() {
        assertThat(query("select indexname from pg_indexes where tablename = 'offer_document'"))
                .contains("offer_document_product_number_idx", "offer_document_ean_gtin_idx");
        assertThat(query("select indexdef from pg_indexes where indexname = 'offer_document_ean_gtin_idx'"))
                .allSatisfy(definition -> assertThat(definition).contains("UNIQUE").doesNotContain("REMOVED"));
    }

    private List<String> query(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
