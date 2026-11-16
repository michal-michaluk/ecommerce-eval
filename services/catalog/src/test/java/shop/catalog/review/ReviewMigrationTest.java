package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The review fragment is the only place the review schema exists: Hibernate never owns it ({@code ddl-auto: none}), so a table that is
 * missing here is missing everywhere. The integration profile recreates the schema on every start, so this runs on a clean database.
 */
@IntegrationTest
class ReviewMigrationTest {

    private static final List<String> TABLES = List.of("review_process", "review_finding", "review_history");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsEveryReviewTable() {
        assertThat(query("select table_name from information_schema.tables where table_schema = 'public'"))
                .containsAll(TABLES);
    }

    @Test
    void appliesEveryReviewChangeSet() {
        assertThat(query("select id from databasechangelog where id like '0005-%'"))
                .containsExactlyInAnyOrder("0005-review-process", "0005-review-finding", "0005-review-history");
    }

    @Test
    void keepsAtMostOneOpenReviewOfAnOfferWithAPartialUniqueIndex() {
        assertThat(query("select indexdef from pg_indexes where indexname = 'review_process_open_review_idx'"))
                .allSatisfy(definition -> assertThat(definition).contains("UNIQUE").contains("REQUESTED"));
    }

    @Test
    void storesTheFrozenCandidateAndTheFindingsOutsideIt() {
        assertThat(query("select data_type from information_schema.columns "
                         + "where table_name = 'review_process' and column_name = 'candidate'"))
                .containsExactly("jsonb");
        assertThat(query("select column_name from information_schema.columns where table_name = 'review_finding'"))
                .contains("source", "finding_position", "rule", "field", "severity", "message");
    }

    private List<String> query(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
