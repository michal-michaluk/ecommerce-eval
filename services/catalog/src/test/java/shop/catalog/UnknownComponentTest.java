package shop.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import shop.catalog.offer.CatalogHttpFixture;
import shop.catalog.offer.OfferDocumentFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static shop.catalog.offer.CatalogHttpFixture.AUTHOR_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.as;

/**
 * 12's D18 at the wire: a body is the components its command declares and nothing else, so a typo is `400 {errors[]}` and never a
 * value silently dropped. Driven through the real servlet stack, because the leniency belongs to the mapper the web layer reads
 * with — not to a controller, and not to the mapper the documents are stored with (a published payload stays readable across
 * versions, which is why the repository round trips keep passing and {@code tools.JsonConfigurationTest} still pins the lenient one).
 *
 * <p>The review resource is the one 12 gives a nested component to ({@code override}), so an unknown key inside the request and an
 * {@code override} where a decision belongs are both visible in one place. The lever is the service's, not that route's: every
 * request body is now read strictly, which is what D18 asks of every `PATCH` — the pricing edit answers the same `400` for a key it
 * does not declare.
 */
@IntegrationTest
class UnknownComponentTest {

    static final String REVIEW = "/offers/OFR-1A2B3C4D/review";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void refusesAnUnknownTopLevelComponentAndNamesIt() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                at(REVIEW, HttpMethod.POST, "{\"titlee\":\"typo\"}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"titlee\"}]}");
    }

    @Test
    void refusesAnUnknownNestedComponentAndNamesIt() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                at(REVIEW, HttpMethod.POST, "{\"override\":{\"findings\":[],\"rules\":[\"QP-2\"]}}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"rules\"}]}");
    }

    @Test
    void refusesAnOverrideOnTheDecisionThatNeverCarriesOne() throws Exception {
        MockHttpServletResponse response = as(Scopes.REVIEWER, CatalogHttpFixture.REVIEWER_CALLER, mvc,
                at(REVIEW, HttpMethod.PATCH, "{\"decision\":\"accepted\",\"override\":{\"findings\":[\"QP-2\"]}}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"override\"}]}");
    }

    @Test
    void refusesAnUnknownComponentOnAnotherContextsEditToo() throws Exception {
        MockHttpServletResponse response = as(Scopes.SALES, CatalogHttpFixture.SALES_CALLER, mvc,
                at("/offers/OFR-1A2B3C4D/pricing", HttpMethod.PATCH, "{\"typo\":[1]}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"typo\"}]}");
    }

    @Test
    void stillReadsAKnownBody() throws Exception {
        String create = """
                {"title":"LEGO Star Wars","productNumber":"%s","eanGtin":"%s","categoryPath":["Dziecko"]}"""
                .formatted(OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin());

        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, at("/offers", POST, create));

        assertThat(response.getStatus()).isEqualTo(201);
    }

    private static MockHttpServletRequestBuilder at(String path, HttpMethod method, String body) {
        return request(method, path).contentType(APPLICATION_JSON).content(body);
    }
}
