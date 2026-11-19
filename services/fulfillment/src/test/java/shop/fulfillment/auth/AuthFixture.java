package shop.fulfillment.auth;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * The Keycloak an {@code auth-test} run talks to: one container, the realm of {@code iot-realm.json}, and one bearer token per
 * test user.
 *
 * <p>Each of element 11's scopes is a Keycloak <em>client scope</em> and one client per scope, because that is the one way a
 * value reaches the token's {@code scope} claim without a protocol mapper. A test user therefore signs in through the client
 * of its scope — the two warehouse employees share the employee client, since they differ in identity and not in permission —
 * and the token it gets back carries exactly the authority {@code AppConfiguration} checks.
 */
@Slf4j
@Component
@Profile("auth-test")
public class AuthFixture {

    private static final Map<String, String> CLIENTS = Map.of(
            "employee", "fulfillment-employee-client",
            "other-employee", "fulfillment-employee-client",
            "resolver", "fulfillment-resolver-client",
            "customer", "fulfillment-customer-client",
            "shopping", "shopping-service-client");

    private final KeycloakContainer keycloak;
    private final WebClient client;

    public AuthFixture() {
        StopWatch watch = StopWatch.createStarted();
        log.info("keycloak starting...");
        keycloak = new KeycloakContainer()
                .withReuse(true)
                .withRealmImportFile("/iot-realm.json");
        keycloak.start();
        watch.stop();
        log.info("keycloak started {} in {}", keycloak.getAuthServerUrl(), watch.formatTime());
        System.setProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                keycloak.getAuthServerUrl() + "realms/iot");

        client = WebClient.builder()
                .baseUrl(keycloak.getAuthServerUrl())
                .build();
    }

    @PreDestroy
    public void clean() {
        if (!keycloak.isShouldBeReused()) {
            keycloak.stop();
        }
    }

    /**
     * A token of one test user, whose password is its username.
     *
     * @param username one of {@code employee}, {@code other-employee}, {@code resolver}, {@code customer}, {@code shopping}
     * @return the bearer token carrying that user's single scope
     */
    public String token(String username) {
        AccessToken response = client.post()
                .uri("realms/iot/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("scope", "openid")
                        .with("username", username)
                        .with("password", username)
                        .with("grant_type", "password")
                        .with("client_id", CLIENTS.getOrDefault(username, username + "-client"))
                        .with("client_secret", "secret")
                )
                .retrieve()
                .bodyToMono(AccessToken.class)
                .timeout(Duration.ofSeconds(10))
                .block();
        Objects.requireNonNull(response, "auth response from keycloak");
        return response.access_token();
    }

    record AccessToken(String access_token, String refresh_token, String id_token, String token_type, int expires_in, String scope) {}
}
