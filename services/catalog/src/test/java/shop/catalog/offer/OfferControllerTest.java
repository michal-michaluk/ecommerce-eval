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
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static shop.catalog.offer.CatalogHttpFixture.AUTHOR_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.PHOTOGRAPHER_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.REVIEWER_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.SALES_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.as;
import static shop.catalog.offer.CatalogHttpFixture.json;
import static shop.catalog.offer.CatalogHttpFixture.jsonList;
import static shop.catalog.offer.CatalogHttpFixture.offerIdOf;
import static shop.catalog.offer.CatalogHttpFixture.publishedOffer;

/**
 * 12's offer resource over the real chain: the create with its `201` and its {@code Location} (D19a), the paged list with D16's bounds,
 * the state read, and the removal as a state edit (D13b) — each with the scope the auth matrix grants it and the
 * `400`/`403`/`404`/`409` the status table owns.
 */
@IntegrationTest
class OfferControllerTest {

    static final String TITLE = "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423";
    static final String OFFER_PATH = "/offers/OFR-[A-Z0-9]{8}";

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
    void createsTheOfferWith201AndItsLocation() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create());

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).matches(OFFER_PATH);
        String offerId = offerIdOf(response);
        assertThat(offerId).matches("OFR-[A-Z0-9]{8}");
        JsonAssert.assertThat(response.getContentAsString()).isExactlyLike("""
                {"offerId":"%s","offerState":"draft","offerVersion":null,"availabilityStart":null}""", offerId);
    }

    @Test
    void createsTheOfferUnderAnIdentityOfItsOwnContext() throws Exception {
        String first = offerIdOf(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create()));
        String second = offerIdOf(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create()));

        assertThat(second).isNotEqualTo(first).matches("OFR-[A-Z0-9]{8}");
    }

    @Test
    void refusesASecondOfferForAnIdentifierAlreadyTaken() throws Exception {
        String productNumber = OfferDocumentFixture.uniqueProductNumber();
        String eanGtin = OfferDocumentFixture.uniqueEanGtin();
        as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create(productNumber, eanGtin));

        MockHttpServletResponse duplicate = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create(productNumber, eanGtin));

        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(duplicate.getContentAsString()).isEqualTo("{\"reason\":\"PRODUCT_NUMBER_TAKEN\"}");
    }

    @Test
    void listsOnePageAndThePageAfterIt() throws Exception {
        String first = offerIdOf(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create()));
        String second = offerIdOf(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, create()));

        MockHttpServletResponse page0 = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=0&size=1"));
        MockHttpServletResponse page1 = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=1&size=1"));

        assertThat(jsonList(page0, "$.items").size()).isEqualTo(1);
        assertThat(jsonList(page1, "$.items").size()).isEqualTo(1);
        assertThat(json(page0, "$.items[0].offerId")).isNotEqualTo(json(page1, "$.items[0].offerId"));
        assertThat((Integer) json(page0, "$.page.totalElements")).isGreaterThanOrEqualTo(2);
        assertThat(json(page0, "$.page.size")).isEqualTo(1);
        assertThat(new String[]{first, second}).contains((String) json(page0, "$.items[0].offerId"));
    }

    @Test
    void requiresBothPagingParameters() throws Exception {
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers")).getStatus()).isEqualTo(400);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=0")).getStatus()).isEqualTo(400);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?size=10")).getStatus()).isEqualTo(400);
    }

    @Test
    void refusesAPageOutsideTheBoundsOfD16() throws Exception {
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=-1&size=10")).getStatus())
                .isEqualTo(400);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=0&size=0")).getStatus())
                .isEqualTo(400);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=0&size=101")).getStatus())
                .isEqualTo(400);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers?page=0&size=100")).getStatus())
                .isEqualTo(200);
    }

    @Test
    void readsTheOfferStateAndItsCurrentVersion() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers/" + offerId));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(response.getContentAsString()).hasFieldsLike("""
                {"offerId":"%s","offerState":"published","offerVersion":1}""", offerId);
    }

    @Test
    void answersAnUnknownOfferWith404AndNoBody() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers/OFR-00000000"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void removesAPublishedOfferAsAStateEdit() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, removal(offerId));

        assertThat(response.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(response.getContentAsString()).hasFieldsLike("""
                {"offerId":"%s","offerState":"removed"}""", offerId);
    }

    @Test
    void refusesRemovingADraftWith409AndTheOwnersReason() throws Exception {
        String offerId = CatalogHttpFixture.draftOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc, removal(offerId));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"REMOVAL_NEEDS_PUBLICATION\"}");
    }

    @Test
    void refusesTheRemovalToThePhotoAndTheSalesScopes() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse asPhotographer = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc, removal(offerId));
        MockHttpServletResponse asSales = as(Scopes.SALES, SALES_CALLER, mvc, removal(offerId));

        assertThat(asPhotographer.getStatus()).isEqualTo(403);
        assertThat(asPhotographer.getContentAsString()).isEmpty();
        assertThat(asSales.getStatus()).isEqualTo(403);
        assertThat(asSales.getContentAsString()).isEmpty();
    }

    @Test
    void refusesAnUnknownComponentWith400AndItsField() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc,
                request(PATCH, "/offers/" + offerId).contentType(APPLICATION_JSON)
                        .content("{\"offerState\":\"removed\",\"offerStatus\":\"removed\"}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"offerStatus\"}]}");
    }

    @Test
    void refusesAStateNoClientMayName() throws Exception {
        String offerId = publishedOffer(offers);

        MockHttpServletResponse response = as(Scopes.REVIEWER, REVIEWER_CALLER, mvc,
                request(PATCH, "/offers/" + offerId).contentType(APPLICATION_JSON)
                        .content("{\"offerState\":\"published\"}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"offerState\"}]}");
    }

    @Test
    void refusesAnInvalidCheckDigitWith400AndItsField() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(POST, "/offers").contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"LEGO","productNumber":"1","eanGtin":"%s","categoryPath":["Dziecko"]}"""
                                .formatted(CatalogHttpFixture.INVALID_EAN_GTIN)));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"eanGtin\"}]}");
    }

    @Test
    void refusesACreateWithoutTheAuthorsScope() throws Exception {
        MockHttpServletResponse response = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc, create());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create() {
        return create(OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(String productNumber,
                                                                                                   String eanGtin) {
        return request(POST, "/offers").contentType(APPLICATION_JSON).content("""
                {"title":"%s",
                 "productNumber":"%s",
                 "eanGtin":"%s",
                 "categoryPath":["Dziecko","Zabawki","Klocki","LEGO","Zestawy","Star Wars"]}"""
                .formatted(TITLE, productNumber, eanGtin));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder removal(String offerId) {
        return request(PATCH, "/offers/" + offerId).contentType(APPLICATION_JSON).content("{\"offerState\":\"removed\"}");
    }
}
