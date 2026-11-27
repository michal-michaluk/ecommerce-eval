package shop.shopping;

import com.jayway.jsonpath.JsonPath;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The callers of element 07 and the reader of what they were answered.
 *
 * <p>A caller is a bearer token the resource server would have produced from a JWT: the customer's carries the one scope the
 * routes demand ({@link Scopes#CUSTOMER}), and the service's carries the scope the realm's own {@code shopping-service} client
 * grants — and, as {@code CustomerIdentityTokenTest} proves against a real Keycloak, no customer scope at all, which is what
 * DoD16's {@code 403} stands on. The subject is the customer the basket belongs to (element 07 §Auth).
 */
public class HttpFixture {

    /** The realm-{@code shopping} service client's scope, spelled the way Spring Security reads a token's {@code scope} claim. */
    public static final String SERVICE = "SCOPE_shopping-service";

    private HttpFixture() {
    }

    public static MockHttpServletResponse asCustomer(MockMvc mvc, String customerId,
            MockHttpServletRequestBuilder request) throws Exception {
        return perform(mvc, request, Scopes.CUSTOMER, customerId);
    }

    /** The same caller on another channel: the claim element 04 reads, {@code smart} or anything else (D54). */
    public static MockHttpServletResponse asCustomerOnChannel(MockMvc mvc, String customerId, String channel,
            MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt()
                        .authorities(new SimpleGrantedAuthority(Scopes.CUSTOMER))
                        .jwt(token -> token.subject(customerId).claim("channel", channel))))
                .andReturn().getResponse();
    }

    public static MockHttpServletResponse asService(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return perform(mvc, request, SERVICE, "service");
    }

    public static MockHttpServletResponse anonymous(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    /** A customer no other test shares: the auto-created basket of one is committed outside the test's transaction. */
    public static String aCustomer() {
        return "C-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString();
    }

    /** The value at {@code jsonPath}, read the way the contract's captures do. */
    public static Object json(MockHttpServletResponse response, String jsonPath) throws Exception {
        return JsonPath.read(body(response), jsonPath);
    }

    private static MockHttpServletResponse perform(MockMvc mvc, MockHttpServletRequestBuilder request, String scope,
            String subject) throws Exception {
        return mvc.perform(request.with(jwt()
                        .authorities(new SimpleGrantedAuthority(scope))
                        .jwt(token -> token.subject(subject))))
                .andReturn().getResponse();
    }
}
