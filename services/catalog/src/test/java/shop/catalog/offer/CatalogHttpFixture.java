package shop.catalog.offer;

import com.jayway.jsonpath.JsonPath;
import lombok.SneakyThrows;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The four callers of 12 and the offers the HTTP routes are proven on.
 *
 * <p>The offers are built through the primary port and not through the routes: a route test asserts one route, so its precondition is
 * the shortest write that reaches the state — and every seed carries identifiers of its own, because a stored offer keeps its
 * {@code productNumber}/ {@code eanGtin} for good (RULE-11).
 */
public class CatalogHttpFixture {

    public static final String AUTHOR_CALLER = "ana.author";
    public static final String REVIEWER_CALLER = "reviewer.kowalska";
    public static final String PHOTOGRAPHER_CALLER = "photographer.nowak";
    public static final String SALES_CALLER = "sales.kowalczyk";

    public static final String INVALID_EAN_GTIN = "5702017817706";

    /**
     * The response of {@code caller} holding {@code scope}: the bearer token is the one the resource server would have produced from a
     * JWT carrying that single scope, so the real filter chain decides every request.
     */
    public static MockHttpServletResponse as(String scope, String caller, MockMvc mvc, MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.with(jwt()
                        .authorities(new SimpleGrantedAuthority(scope))
                        .jwt(token -> token.subject(caller))))
                .andReturn().getResponse();
    }

    /** The value at {@code jsonPath}, read the way 12's prototype asserts it. */
    @SneakyThrows
    public static Object json(MockHttpServletResponse response, String jsonPath) {
        return JsonPath.read(response.getContentAsString(StandardCharsets.UTF_8), jsonPath);
    }

    /** The collection at {@code jsonPath}, for the assertions a `jsonpath` matcher would make one element at a time. */
    @SuppressWarnings("unchecked")
    public static List<Object> jsonList(MockHttpServletResponse response, String jsonPath) {
        return (List<Object>) json(response, jsonPath);
    }

    /** A draft the routes can edit: the title and identifiers of 01's Instance A, with identifiers no other offer carries. */
    public static String draftOffer(OfferService offers) {
        String offerId = OfferDocumentFixture.uniqueOfferId();
        offers.draftOffer(offerId, draft());
        return offerId;
    }

    /** A published offer with one frozen version: the state a removal, a revert and a publish history all start from. */
    public static String publishedOffer(OfferService offers) {
        String offerId = draftOffer(offers);
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        offers.scheduleAvailability(offerId, OfferFixture.AVAILABILITY_START);
        offers.publish(offerId, OfferFixture.now(), REVIEWER_CALLER);
        return offerId;
    }

    public static ProductDescription.Draft draft() {
        return ProductDescriptionDraftFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin());
    }

    public static String offerIdOf(MockHttpServletResponse response) {
        String location = response.getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
