package shop.shopping;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import shop.shopping.auth.AuthFixture;

import java.util.List;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity exercised end to end: a real Keycloak holds the service's own realm document, and
 * Spring Security's default converter — the one a chain installs when it configures none, as
 * {@code AppConfiguration} does — turns the customer token's {@code scope} claim into the authority
 * every customer route will demand (element 07, D27). The service token is valid in the same realm
 * and carries no such authority, which is what DoD16's {@code 403} stands on; a token from a foreign
 * realm never reaches this check because the issuer rejects it first.
 */
@TestInstance(PER_CLASS)
class CustomerIdentityTokenTest {

    private AuthFixture auth;

    @BeforeAll
    void importTheRealm() {
        auth = new AuthFixture();
    }

    @Test
    void turnsTheCustomerTokenIntoTheAuthorityTheRoutesWillDemand() {
        assertThat(authoritiesOf(auth.customerToken())).contains(Scopes.CUSTOMER);
    }

    @Test
    void keysTheCustomerOnTheSubjectTheBasketBelongsTo() {
        assertThat(auth.customerToken().getSubject()).isNotBlank();
    }

    @Test
    void givesTheServiceTokenNoCustomerAuthority() {
        assertThat(authoritiesOf(auth.serviceToken())).doesNotContain(Scopes.CUSTOMER);
    }

    @Test
    void keepsTheRealmAndItsKeysAcrossASecondFixture() {
        Jwt customer = auth.customerToken();
        JwtDecoder decoderBuiltBefore = JwtDecoders.fromIssuerLocation(auth.issuer());

        new AuthFixture();

        assertThat(auth.customerToken().getSubject()).isEqualTo(customer.getSubject());
        assertThat(decoderBuiltBefore.decode(customer.getTokenValue()).getSubject()).isEqualTo(customer.getSubject());
    }

    private static List<String> authoritiesOf(Jwt token) {
        AbstractAuthenticationToken authentication = new JwtAuthenticationConverter().convert(token);

        return authentication.getAuthorities().stream().map(Object::toString).toList();
    }
}
