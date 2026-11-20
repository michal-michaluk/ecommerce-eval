package shop.fulfillment;

import com.jayway.jsonpath.JsonPath;
import lombok.SneakyThrows;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The callers of element 11 and the reader of what they were answered.
 *
 * <p>The bearer token is the one the resource server would have produced from a JWT carrying that single scope, so the real
 * filter chain decides every request: a route the matrix does not grant answers {@code 403} here exactly as it would in the
 * deployment.
 */
public class HttpFixture {

    public static final String EMPLOYEE_SCOPE = "SCOPE_fulfillment-employee";
    public static final String RESOLVER_SCOPE = "SCOPE_fulfillment-resolver";
    public static final String CUSTOMER_SCOPE = "SCOPE_fulfillment-customer";
    public static final String SHOPPING_SCOPE = "SCOPE_shopping-service";

    public static final String EMPLOYEE = "employee-42";
    public static final String OTHER_EMPLOYEE = "employee-17";
    public static final String RESOLVER = "resolver-7";
    public static final String CUSTOMER = "customer-1";
    public static final String SHOPPING = "shopping-service";

    private HttpFixture() {
    }

    public static MockHttpServletResponse as(String scope, String caller, MockMvc mvc,
                                             MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt()
                        .authorities(new SimpleGrantedAuthority(scope))
                        .jwt(token -> token.subject(caller))))
                .andReturn().getResponse();
    }

    public static MockHttpServletResponse anonymous(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    /** The value at {@code jsonPath}, read the way element 11's prototype asserts it. */
    @SneakyThrows
    public static Object json(MockHttpServletResponse response, String jsonPath) {
        return JsonPath.read(response.getContentAsString(StandardCharsets.UTF_8), jsonPath);
    }

    public static String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(StandardCharsets.UTF_8);
    }
}
