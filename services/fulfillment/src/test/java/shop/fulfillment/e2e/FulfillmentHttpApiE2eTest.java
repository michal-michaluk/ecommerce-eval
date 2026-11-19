package shop.fulfillment.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import shop.fulfillment.kernel.WarehouseStock;
import shop.fulfillment.processing.FakeWarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The HTTP contract of element 11 is real, including every error path and every scope.
 *
 * <p>The suite of {@code e2e/fulfillment-api.hurl} is the deliverable — this class only gives it the world it needs: the
 * container set of the integration profile comes up as it does for every integration test, the application boots on one
 * fixed port, Keycloak installs the realm of the four scopes, and the run's tokens arrive as variables.
 *
 * <p>The suite is not part of {@code build}: it boots the service and drives hurl against it in minutes, not seconds, so
 * {@code ./gradlew e2eHurl} is the one command that owns it — and a failing request fails that command.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "server.port=" + FulfillmentE2eVariables.APP_PORT)
@ActiveProfiles("integration-test")
class FulfillmentHttpApiE2eTest {

    @DynamicPropertySource
    static void world(DynamicPropertyRegistry registry) {
        FulfillmentRealmFixture.installRealm();
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", FulfillmentRealmFixture::issuer);
    }

    @TestConfiguration
    static class Warehouse {

        @Bean
        WarehouseStock warehouseStock() {
            return new FakeWarehouseStock();
        }
    }

    @Test
    void runsEveryRequestOfTheContractOfElement11() {
        assertThat(HurlSuiteFixture.run(FulfillmentE2eVariables.current()))
                .as("hurl --test e2e/fulfillment-api.hurl — the report above names the first request that is not green")
                .isZero();
    }
}
