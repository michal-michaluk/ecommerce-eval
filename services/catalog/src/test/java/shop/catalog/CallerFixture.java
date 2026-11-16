package shop.catalog;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Who a controller slice answers as: 12's handlers read the caller and the decision they own from the security context, so a test
 * states one name and one scope instead of building the token.
 */
public final class CallerFixture {

    private CallerFixture() {
    }

    public static void actsAs(String user, String... scopes) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, "n/a", scopes));
    }

    public static void forgets() {
        SecurityContextHolder.clearContext();
    }
}
