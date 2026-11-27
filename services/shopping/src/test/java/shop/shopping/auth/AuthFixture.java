package shop.shopping.auth;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The realm the identity needs, exercised instead of read: one Keycloak holds the service's own
 * {@code shopping-realm.json} and mints the two bearer tokens that document provisions — the
 * customer's, whose {@code scope} claim carries {@code customer}, and the service's, which is valid
 * in the same realm and carries no customer scope (DoD16).
 *
 * <p>The realm is installed once per JVM through the admin API, after the container's own
 * {@code --import-realm}: a reused container serves whatever it imported when it was created, so the
 * file import alone would hand the second run a stale realm. Delete-then-create is what
 * {@code shop.fulfillment.e2e.FulfillmentRealmFixture} does for its own container; here it runs
 * behind a guard, because recreating a realm issues new keys and a {@code JwtDecoder} another test
 * class already built would then reject every token minted before the recreation.
 *
 * <p>A token comes back decoded by the decoder a resource server builds from the issuer, so a test
 * asserts against a signature-validated token rather than a string. The issuer property is set here
 * as well, because a Spring context started after this bean reads the container's address from
 * nowhere else.
 */
@Slf4j
@Component
@Profile("auth-test")
public class AuthFixture {

    private static final String REALM = "shopping";
    private static final String SECRET = "secret";
    private static final String REALM_DOCUMENT = "/shopping-realm.json";
    private static final String ADMIN_REALMS = "/admin/realms";

    private static final Map<String, String> CLIENTS = Map.of(
            "customer", "customer-client",
            "service", "shopping-service-client");

    private static final KeycloakContainer KEYCLOAK = startKeycloak();
    private static final WebClient API = WebClient.create(KEYCLOAK.getAuthServerUrl());
    private static final AtomicBoolean REALM_INSTALLED = new AtomicBoolean();

    private JwtDecoder decoder;

    public AuthFixture() {
        System.setProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", issuer());
        installRealmOnce();
    }

    public String issuer() {
        return KEYCLOAK.getAuthServerUrl().replaceAll("/+$", "") + "/realms/" + REALM;
    }

    public Jwt customerToken() {
        return decode(token("customer"));
    }

    public Jwt serviceToken() {
        return decode(token("service"));
    }

    private void installRealmOnce() {
        if (REALM_INSTALLED.compareAndSet(false, true)) {
            installRealm();
        }
    }

    private void installRealm() {
        String administrator = administratorToken();
        removeRealm(administrator);
        admin(administrator).post()
                .uri(ADMIN_REALMS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(realmDocument())
                .retrieve()
                .toBodilessEntity()
                .block();
        log.info("realm {} installed from {}", REALM, REALM_DOCUMENT);
    }

    private Jwt decode(String token) {
        return decoder().decode(token);
    }

    private JwtDecoder decoder() {
        if (decoder == null) {
            decoder = JwtDecoders.fromIssuerLocation(issuer());
        }
        return decoder;
    }

    private String token(String username) {
        AccessToken response = API.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("scope", "openid")
                        .with("client_id", CLIENTS.get(username))
                        .with("client_secret", SECRET)
                        .with("username", username)
                        .with("password", username)
                        .with("grant_type", "password"))
                .retrieve()
                .bodyToMono(AccessToken.class)
                .block();
        Objects.requireNonNull(response, "token of " + username);
        return response.access_token();
    }

    private String administratorToken() {
        AccessToken token = API.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", "admin-cli")
                        .with("username", KEYCLOAK.getAdminUsername())
                        .with("password", KEYCLOAK.getAdminPassword()))
                .retrieve()
                .bodyToMono(AccessToken.class)
                .block();
        return Objects.requireNonNull(token, "administrator token").access_token();
    }

    private void removeRealm(String administrator) {
        admin(administrator).delete()
                .uri(ADMIN_REALMS + "/{realm}", REALM)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(WebClientResponseException.NotFound.class, absent -> Mono.empty())
                .block();
    }

    private WebClient admin(String administrator) {
        return API.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + administrator).build();
    }

    private String realmDocument() {
        try (InputStream document = new ClassPathResource(REALM_DOCUMENT).getInputStream()) {
            return new String(document.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + REALM_DOCUMENT, unreadable);
        }
    }

    private static KeycloakContainer startKeycloak() {
        KeycloakContainer keycloak = new KeycloakContainer()
                .withReuse(true)
                .withRealmImportFile(REALM_DOCUMENT);
        keycloak.start();
        log.info("keycloak started at {}", keycloak.getAuthServerUrl());
        return keycloak;
    }

    record AccessToken(String access_token, String refresh_token, String id_token, String token_type, int expires_in, String scope) {
    }
}
