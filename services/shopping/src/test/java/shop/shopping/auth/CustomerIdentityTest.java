package shop.shopping.auth;

import org.junit.jupiter.api.Test;
import shop.shopping.tools.JsonConfiguration;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Element 07's customer identity as the service's own realm document declares it: the scope reaches
 * the token, the clients grant it and withhold it, and the two users the cluster mints from exist.
 * No Spring context and no container — this test guards the document, {@link CustomerIdentityTokenTest}
 * exercises it against a real Keycloak.
 */
class CustomerIdentityTest {

    private static final JsonNode REALM = realmDocument();

    @Test
    void namesItsOwnRealm() {
        assertThat(REALM.get("realm").asString()).isEqualTo("shopping");
    }

    @Test
    void putsTheCustomerScopeInTheToken() {
        JsonNode included = entry("clientScopes", "name", "customer")
                .path("attributes")
                .get("include.in.token.scope");

        assertThat(included).isNotNull();
        assertThat(included.asString()).isEqualTo("true");
    }

    @Test
    void grantsTheCustomerScopeToTheCustomerClient() {
        JsonNode client = entry("clients", "clientId", "customer-client");

        assertThat(client.get("secret").asString()).isEqualTo("secret");
        assertThat(client.get("directAccessGrantsEnabled").asBoolean()).isTrue();
        assertThat(client.get("standardFlowEnabled").asBoolean()).isFalse();
        assertThat(defaultScopesOf(client)).contains("basic", "customer");
    }

    @Test
    void grantsTheServiceIdentityNoCustomerScope() {
        JsonNode client = entry("clients", "clientId", "shopping-service-client");

        assertThat(client.get("secret").asString()).isEqualTo("secret");
        assertThat(client.get("directAccessGrantsEnabled").asBoolean()).isTrue();
        assertThat(defaultScopesOf(client)).contains("shopping-service").doesNotContain("customer");
    }

    @Test
    void provisionsTheUserTheClusterMintsWith() {
        JsonNode password = entry("users", "username", "customer").get("credentials").get(0);

        assertThat(password.get("type").asString()).isEqualTo("password");
        assertThat(password.get("value").asString()).isEqualTo("customer");
        assertThat(password.get("temporary").asBoolean()).isFalse();
    }

    @Test
    void provisionsTheServiceUserTheClusterMintsWith() {
        JsonNode password = entry("users", "username", "service").get("credentials").get(0);

        assertThat(password.get("value").asString()).isEqualTo("service");
    }

    private static JsonNode entry(String arrayKey, String nameField, String name) {
        return StreamSupport.stream(REALM.get(arrayKey).spliterator(), false)
                .filter(candidate -> named(candidate, nameField, name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + arrayKey + " entry named " + name));
    }

    private static boolean named(JsonNode candidate, String field, String name) {
        JsonNode value = candidate.get(field);

        return value != null && name.equals(value.asString());
    }

    private static List<String> defaultScopesOf(JsonNode client) {
        return StreamSupport.stream(client.get("defaultClientScopes").spliterator(), false)
                .map(JsonNode::asString)
                .toList();
    }

    private static JsonNode realmDocument() {
        try (InputStream document = Objects.requireNonNull(
                CustomerIdentityTest.class.getResourceAsStream("/shopping-realm.json"))) {
            return JsonConfiguration.OBJECT_MAPPER.readTree(document);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
