package shop.catalog.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5 of the spec's Prove Definition of Done: the HTTP contract of 12 is real, including every error path and every scope.
 *
 * <p>The suite of {@code e2e/catalog-http-api.hurl} is the deliverable — this class only gives it the world it needs, because a
 * hurl file cannot start a database: the container set of the integration profile (Postgres, Kafka) comes up as it does for every
 * integration test, the events topic is waited for until its leader is elected, the application boots on one fixed port, Keycloak
 * installs the realm of the four scopes, and the run's dates and tokens arrive as variables (D35, {@code testing.md}).
 *
 * <p>The suite is not part of {@code build}: it runs the whole prototype end to end in minutes, not seconds, so {@code ./gradlew
 * e2eHurl} is the one command that owns it — and a failing request fails that command.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + CatalogE2eVariables.APP_PORT)
@ActiveProfiles("integration-test")
class CatalogHttpApiE2eTest {

    @DynamicPropertySource
    static void worlds(DynamicPropertyRegistry registry) {
        CatalogRealmFixture.installRealm();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", CatalogRealmFixture::issuer);
    }

    @Test
    void runsEveryRequestOfTheContractOfElement12() {
        CatalogTopicFixture.awaitLeader();
        assertThat(HurlSuiteFixture.run(CatalogE2eVariables.current()))
                .as("hurl --test e2e/catalog-http-api.hurl — the report above names the first request that is not green")
                .isZero();
    }
}
