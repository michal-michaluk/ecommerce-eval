package shop.catalog.offer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import shop.catalog.IntegrationTest;
import shop.catalog.JsonAssert;
import shop.catalog.Scopes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static shop.catalog.offer.CatalogHttpFixture.AUTHOR_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.REVIEWER_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.SALES_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.as;
import static shop.catalog.offer.CatalogHttpFixture.json;
import static shop.catalog.offer.CatalogHttpFixture.jsonList;
import static shop.catalog.offer.CatalogHttpFixture.publishedOffer;

/**
 * 12's publish history (D31) and the revert that appends to it (D19): the frozen versions in the order they were published, the version
 * a revert freezes and names in its {@code Location}, the paged history of D16, and the `201`/`403`/`404`/`409` the status table owns.
 */
@IntegrationTest
class OfferVersionsControllerTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    OfferService offers;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listsTheFrozenVersionsInTheOrderTheyWerePublished() throws Exception {
        String offerId = publishedOffer(offers);
        as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert(offerId, 1));

        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=0&size=20"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(jsonList(response, "$.items[*].offerVersion")).containsExactly(1, 2);
        assertThat(json(response, "$.items[0].copiedFrom")).isNull();
        assertThat(json(response, "$.items[1].copiedFrom")).isEqualTo(1);
        assertThat(json(response, "$.items[1].publishedBy")).isEqualTo(REVIEWER_CALLER);
        assertThat(json(response, "$.items[1].publishedAt")).isNotNull();
        assertThat(json(response, "$.page.totalElements")).isEqualTo(2);
    }

    @Test
    void pagesTheHistoryWithTheOffsetTheClientAskedFor() throws Exception {
        String offerId = publishedOffer(offers);
        as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert(offerId, 1));

        MockHttpServletResponse second = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=1&size=1"));
        MockHttpServletResponse pastTheEnd = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=5&size=1"));

        assertThat(jsonList(second, "$.items[*].offerVersion")).containsExactly(2);
        assertThat(json(second, "$.page.number")).isEqualTo(1);
        assertThat(jsonList(pastTheEnd, "$.items")).isEmpty();
        assertThat(json(pastTheEnd, "$.page.totalElements")).isEqualTo(2);
    }

    @Test
    void revertsOlderContentIntoTheNextVersionWith201AndItsLocation() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert(offerId, 1));

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/offers/" + offerId + "/versions/2");
        JsonAssert.assertThat(response.getContentAsString()).hasFieldsLike("""
                {"offerId":"%s","offerState":"published","offerVersion":2,"copiedFrom":1}""", offerId);
    }

    @Test
    void refusesARevertOfAVersionTheOfferDoesNotHave() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert(offerId, 7));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEmpty();
    }

    /** D37: a review that is open freezes what is published, so a revert below it is refused by the aggregate itself. */
    @Test
    void refusesARevertWhileTheCandidateIsUnderReview() throws Exception {
        String offerId = publishedOffer(offers);
        offers.requestReview(offerId);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert(offerId, 1));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"REVIEW_OPEN\"}");
    }

    @Test
    void refusesAnUnknownComponentWith400AndItsField() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc,
                request(POST, "/offers/" + offerId + "/versions").contentType(APPLICATION_JSON)
                        .content("{\"copyFrom\": 1, \"copyForm\": 2}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"copyForm\"}]}");
    }

    @Test
    void allowsOnlyTheReviewerToRevert() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse asAuthor = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, revert(offerId, 1));
        MockHttpServletResponse asSales = as(Scopes.SALES, SALES_CALLER, mvc, revert(offerId, 1));

        assertThat(asAuthor.getStatus()).isEqualTo(403);
        assertThat(asAuthor.getContentAsString()).isEmpty();
        assertThat(asSales.getStatus()).isEqualTo(403);
        assertThat(asSales.getContentAsString()).isEmpty();
    }

    @Test
    void requiresBothPagingParameters() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse neither = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions"));
        MockHttpServletResponse noSize = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=0"));

        assertThat(neither.getStatus()).isEqualTo(400);
        assertThat(neither.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"page\"}]}");
        assertThat(noSize.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"size\"}]}");
    }

    @Test
    void refusesAPageOutsideTheBoundsOfD16() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse negativePage = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=-1&size=10"));
        MockHttpServletResponse tooWide = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/versions?page=0&size=101"));

        assertThat(negativePage.getStatus()).isEqualTo(400);
        assertThat(negativePage.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"page\"}]}");
        assertThat(tooWide.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"size\"}]}");
    }

    @Test
    void answersAnUnknownOfferWith404() throws Exception {
        MockHttpServletResponse read = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/OFR-00000000/versions?page=0&size=20"));
        MockHttpServletResponse revert = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, revert("OFR-00000000", 1));

        assertThat(read.getStatus()).isEqualTo(404);
        assertThat(read.getContentAsString()).isEmpty();
        assertThat(revert.getStatus()).isEqualTo(404);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder revert(String offerId,
                                                                                                  int copyFrom) {
        return request(POST, "/offers/" + offerId + "/versions")
                .contentType(APPLICATION_JSON)
                .content("{\"copyFrom\": %s}".formatted(copyFrom));
    }
}
