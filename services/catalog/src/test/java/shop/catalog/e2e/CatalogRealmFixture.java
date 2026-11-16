package shop.catalog.e2e;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The Keycloak the scope matrix of 12 needs: one container for the whole suite and one bearer token per test user.
 *
 * <p>The realm is {@code catalog-realm.json} and it is (re)installed on every run through the admin API instead of
 * {@code --import-realm}: a reused container keeps the realm it imported when it was created, so a file-based import would
 * silently serve a stale matrix to the second run. Delete-then-create is cheap, deterministic and the reason the harness runs
 * green both from a clean machine and against containers a previous run left behind.
 *
 * <p>The realm carries the four scopes of 12 as native <em>client scopes</em> and one client per scope, because that is the one
 * way Keycloak puts a value into the {@code scope} claim — the claim {@code spring.security.oauth2.resourceserver.jwt} reads for
 * authorities — without a protocol mapper: a mapper writing a second {@code scope} claim produces a token with a duplicated JSON
 * key, which Nimbus refuses as a malformed payload. Each test user holds the realm role of the same name and signs in through the
 * client of that scope, so its token carries exactly one scope and {@code SCOPE_catalog-author} is what {@code Scopes} reads.
 */
public final class CatalogRealmFixture {

    private static final Logger log = LoggerFactory.getLogger(CatalogRealmFixture.class);

    private static final String REALM = "catalog";
    private static final String CLIENT_SECRET = "secret";
    private static final String REALM_DOCUMENT = "/catalog-realm.json";
    private static final String ADMIN_REALMS = "/admin/realms";

    private static final KeycloakContainer KEYCLOAK = startKeycloak();
    private static final WebClient API = WebClient.create(KEYCLOAK.getAuthServerUrl());

    private CatalogRealmFixture() {
    }

    /** The issuer a token of this realm carries, which is what the resource server has to trust. */
    public static String issuer() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    }

    public static void installRealm() {
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

    /** The password grant of one test user, through the client of its scope; the password is the username. */
    public static String token(String username) {
        TokenResponse token = API.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", "catalog-" + username + "-client")
                        .with("client_secret", CLIENT_SECRET)
                        .with("username", username)
                        .with("password", username))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
        return Objects.requireNonNull(token, "token of " + username).access_token();
    }

    private static String administratorToken() {
        TokenResponse token = API.post()
                .uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", "admin-cli")
                        .with("username", KEYCLOAK.getAdminUsername())
                        .with("password", KEYCLOAK.getAdminPassword()))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
        return Objects.requireNonNull(token, "administrator token").access_token();
    }

    private static void removeRealm(String administrator) {
        admin(administrator).delete()
                .uri(ADMIN_REALMS + "/{realm}", REALM)
                .retrieve()
                .toBodilessEntity()
                .onErrorResume(WebClientResponseException.NotFound.class, absent -> Mono.empty())
                .block();
    }

    private static WebClient admin(String administrator) {
        return API.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + administrator).build();
    }

    private static String realmDocument() {
        try (InputStream document = new ClassPathResource(REALM_DOCUMENT).getInputStream()) {
            return new String(document.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + REALM_DOCUMENT, unreadable);
        }
    }

    private static KeycloakContainer startKeycloak() {
        KeycloakContainer keycloak = new KeycloakContainer().withReuse(true);
        keycloak.start();
        log.info("keycloak started at {}", keycloak.getAuthServerUrl());
        return keycloak;
    }

    record TokenResponse(String access_token) {
    }
}
