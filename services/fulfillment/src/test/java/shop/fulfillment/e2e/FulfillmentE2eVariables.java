package shop.fulfillment.e2e;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The variables the hurl suite reads: the URL the service answers on and the five tokens of element 11's matrix.
 *
 * <p>No request names a fixed processing id — the suite captures the one the handover minted, so a run asserts the same
 * things whatever year it happens in and whatever the sequence has already handed out.
 */
final class FulfillmentE2eVariables {

    static final int APP_PORT = 18091;

    private FulfillmentE2eVariables() {
    }

    static Map<String, String> current() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("BASE_URL", "http://localhost:" + APP_PORT);
        variables.put("shoppingToken", FulfillmentRealmFixture.token("shopping"));
        variables.put("employeeToken", FulfillmentRealmFixture.token("employee"));
        variables.put("otherEmployeeToken", FulfillmentRealmFixture.token("other-employee"));
        variables.put("resolverToken", FulfillmentRealmFixture.token("resolver"));
        variables.put("customerToken", FulfillmentRealmFixture.token("customer"));
        return variables;
    }
}
