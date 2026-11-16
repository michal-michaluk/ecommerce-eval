package shop.catalog.offer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import shop.catalog.IntegrationTest;
import shop.catalog.Scopes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static shop.catalog.offer.CatalogHttpFixture.AUTHOR_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.PHOTOGRAPHER_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.SALES_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.as;
import static shop.catalog.offer.CatalogHttpFixture.json;
import static shop.catalog.offer.CatalogHttpFixture.jsonList;
import static shop.catalog.offer.CatalogHttpFixture.offerIdOf;
import static shop.catalog.offer.CatalogHttpFixture.publishedOffer;

/**
 * 12's draft resource (D13): one `PATCH` for the description, the media and the availability, the read of 14 behind it, and D13a's
 * field-level scope proven per writer — the author owns the description and the availability, the photographer owns the media, and
 * anything else is `403 FIELD_NOT_IN_SCOPE` before the offer is even loaded, so a wrong scope is never a `404`.
 */
@IntegrationTest
class OfferDraftControllerTest {

    static final String TITLE = "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423";

    static final String DESCRIPTION = """
            {"description": {"parameters": [
                {"code": "childAge", "label": "Wiek dziecka", "value": "6+", "valueType": "rangeFrom", "from": 6, "unit": "lat"},
                {"code": "pieceCount", "label": "Liczba elementów", "value": 581, "valueType": "integer"}],
              "blocks": [
                {"type": "paragraph", "text": "Odkryj nową galaktykę LEGO Star Wars."},
                {"type": "bulletList", "items": ["SMART klocek", "dźwięki i światła"]}]}}""";

    static final String MEDIA = """
            {"media": {"add": [
                {"kind": "image", "format": "jpg", "position": 1, "primary": true, "url": "https://cdn.example/M-01.jpg"},
                {"kind": "image", "format": "jpg", "position": 2, "primary": false, "url": "https://cdn.example/M-02.jpg"},
                {"kind": "video", "format": "mp4", "position": 3, "primary": false, "url": "https://cdn.example/M-03.mp4",
                 "durationSec": 18}]}}""";

    static final String AVAILABILITY = "{\"availabilityStart\": \"2026-07-01\"}";

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
    void editsTheDescriptionAndAnswersTheDraftWithItsFindings() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                draftPatch(offerIdOf(created()), DESCRIPTION));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json(response, "$.description.title")).isEqualTo(TITLE);
        assertThat(json(response, "$.description.parameters[0].code")).isEqualTo("childAge");
        assertThat(json(response, "$.description.parameters[0].valueType")).isEqualTo("rangeFrom");
        assertThat(json(response, "$.description.parameters[1].value")).isEqualTo(581);
        assertThat(json(response, "$.description.blocks[1].type")).isEqualTo("bulletList");
        assertThat(jsonList(response, "$.quality.findings")).isNotEmpty();
        assertThat(json(response, "$.offerState")).isEqualTo("draft");
    }

    @Test
    void letsThePhotographerAddMediaAndSetsItsOrder() throws Exception {
        MockHttpServletResponse response = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc,
                draftPatch(offerIdOf(created()), MEDIA));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json(response, "$.media[0].primary")).isEqualTo(true);
        assertThat(json(response, "$.media[0].mediaId")).isEqualTo("M-01");
        assertThat(json(response, "$.media[2].kind")).isEqualTo("video");
        assertThat(jsonList(response, "$.media[*].position")).containsExactly(1, 2, 3);
        assertThat(jsonList(response, "$.media")).hasSize(3);
    }

    @Test
    void letsTheAuthorSetTheAvailabilityStart() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                draftPatch(offerIdOf(created()), AVAILABILITY));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json(response, "$.availabilityStart")).isEqualTo("2026-07-01");
    }

    @Test
    void readsTheDraftAsTheScreenOf14RendersIt() throws Exception {
        String offerId = offerIdOf(created());
        as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, draftPatch(offerId, DESCRIPTION));
        as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc, draftPatch(offerId, MEDIA));
        as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, draftPatch(offerId, AVAILABILITY));

        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                request(GET, "/offers/" + offerId + "/draft"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json(response, "$.description.parameters[0].code")).isEqualTo("childAge");
        assertThat(json(response, "$.media[0].primary")).isEqualTo(true);
        assertThat(json(response, "$.availabilityStart")).isEqualTo("2026-07-01");
        assertThat(jsonList(response, "$.quality.findings")).isNotNull();
    }

    /** D20: the findings are 08's answer about the draft as it is now, not a stored copy — the image rule stops firing once an image is there. */
    @Test
    void asksTheQualityPolicyOnEveryRead() throws Exception {
        String offerId = offerIdOf(created());
        as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, draftPatch(offerId, DESCRIPTION));

        MockHttpServletResponse before = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers/" + offerId + "/draft"));
        as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc, draftPatch(offerId, MEDIA));
        MockHttpServletResponse after = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers/" + offerId + "/draft"));

        assertThat(jsonList(before, "$.quality.findings[*].rule")).contains("QP-4");
        assertThat(jsonList(after, "$.quality.findings[*].rule")).doesNotContain("QP-4");
    }

    @Test
    void refusesTheDescriptionToThePhotographer() throws Exception {
        assertOutsideScope(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, DESCRIPTION);
    }

    @Test
    void refusesTheMediaToTheAuthor() throws Exception {
        assertOutsideScope(Scopes.AUTHOR, AUTHOR_CALLER, MEDIA);
    }

    @Test
    void refusesTheAvailabilityToThePhotographer() throws Exception {
        assertOutsideScope(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, AVAILABILITY);
    }

    @Test
    void refusesTheWholeDraftToSales() throws Exception {
        MockHttpServletResponse response = as(Scopes.SALES, SALES_CALLER, mvc,
                draftPatch(offerIdOf(created()), AVAILABILITY));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
    }

    /** A wrong scope is a `403`, never the `404` the offer itself would answer: the component check runs before the load. */
    @Test
    void refusesAnOutOfScopeComponentBeforeItLooksTheOfferUp() throws Exception {
        MockHttpServletResponse response = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc,
                draftPatch("OFR-00000000", DESCRIPTION));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
    }

    @Test
    void refusesAnUnknownComponentWith400AndItsField() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                draftPatch(offerIdOf(created()), "{\"titlee\": \"typo\"}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"titlee\"}]}");
    }

    /** D18 below the top level: the keys a client may send inside `description` are the components 01 declares, and no others. */
    @Test
    void refusesAnUnknownComponentInsideTheDescription() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                draftPatch(offerIdOf(created()), "{\"description\": {\"titel\": \"typo\"}}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"titel\"}]}");
    }

    /** D13a names remove, reorder and primary as media edits; 02 has no command for them, so a client is told instead of ignored. */
    @Test
    void refusesAMediaEditTheDomainHasNoCommandFor() throws Exception {
        MockHttpServletResponse response = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc,
                draftPatch(offerIdOf(created()), "{\"media\": {\"remove\": [\"M-01\"]}}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"remove\"}]}");
    }

    @Test
    void refusesAMediaItemWithoutAKindOrALocation() throws Exception {
        MockHttpServletResponse noKind = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc,
                draftPatch(offerIdOf(created()), "{\"media\": {\"add\": [{\"position\": 1, \"url\": \"https://cdn.example/M-01.jpg\"}]}}"));
        MockHttpServletResponse noLocation = as(Scopes.PHOTOGRAPHER, PHOTOGRAPHER_CALLER, mvc,
                draftPatch(offerIdOf(created()), "{\"media\": {\"add\": [{\"kind\": \"image\", \"position\": 1}]}}"));

        assertThat(noKind.getStatus()).isEqualTo(400);
        assertThat(noKind.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"kind\"}]}");
        assertThat(noLocation.getStatus()).isEqualTo(400);
        assertThat(noLocation.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"url\"}]}");
    }

    @Test
    void refusesAParameterValueThatDoesNotFitItsValueType() throws Exception {
        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc,
                draftPatch(offerIdOf(created()), """
                        {"description": {"parameters": [
                            {"code": "pieceCount", "label": "Liczba elementów", "value": "nie-liczba", "valueType": "integer"}],
                          "blocks": [{"type": "paragraph", "text": "opis"}]}}"""));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"value\"}]}");
    }

    @Test
    void answersAnUnknownOfferWith404() throws Exception {
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(GET, "/offers/OFR-00000000/draft")).getStatus())
                .isEqualTo(404);
        assertThat(as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, draftPatch("OFR-00000000", AVAILABILITY)).getStatus())
                .isEqualTo(404);
    }

    @Test
    void refusesAnEditOfARemovedOfferWith409() throws Exception {
        String offerId = publishedOffer(offers);
        offers.removeOffer(offerId);

        MockHttpServletResponse response = as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, draftPatch(offerId, AVAILABILITY));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"OFFER_REMOVED\"}");
    }

    private void assertOutsideScope(String scope, String caller, String body) throws Exception {
        MockHttpServletResponse response = as(scope, caller, mvc, draftPatch(offerIdOf(created()), body));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder draftPatch(
            String offerId, String body) {
        return request(PATCH, "/offers/" + offerId + "/draft").contentType(APPLICATION_JSON).content(body);
    }

    private MockHttpServletResponse created() throws Exception {
        return as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, request(POST, "/offers").contentType(APPLICATION_JSON).content("""
                        {"title":"%s","productNumber":"%s","eanGtin":"%s","categoryPath":["Dziecko","Zabawki","Klocki"]}"""
                .formatted(TITLE, OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin())));
    }
}
