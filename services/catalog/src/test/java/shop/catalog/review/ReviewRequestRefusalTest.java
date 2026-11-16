package shop.catalog.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import shop.catalog.IntegrationTest;
import shop.catalog.Scopes;
import shop.catalog.offer.CatalogHttpFixture;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.quality.QualityFixture;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static shop.catalog.offer.CatalogHttpFixture.AUTHOR_CALLER;
import static shop.catalog.offer.CatalogHttpFixture.as;
import static shop.catalog.review.ReviewFixture.REVIEWER;

/**
 * The two refusals 12's `POST /offers/{offerId}/review` answers with `409 {reason}` over the real servlet stack: the offer's own
 * vocabulary reaches the client unchanged, because the advice reads the enum name of whichever context refused (D49) and the review
 * adapter adds no second list.
 */
@IntegrationTest
class ReviewRequestRefusalTest {

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
    void answersAReviewOfAnOfferWithoutAPrimaryImageWithTheOffersReason() throws Exception {
        MockHttpServletResponse response = startsReviewOf(anOfferUnderEditing());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"MEDIA_IMAGE_MISSING\"}");
    }

    @Test
    void answersAReviewOfARemovedOfferWithTheOffersReason() throws Exception {
        MockHttpServletResponse response = startsReviewOf(aRemovedOffer());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"OFFER_REMOVED\"}");
    }

    private MockHttpServletResponse startsReviewOf(String offerId) throws Exception {
        return as(Scopes.AUTHOR, AUTHOR_CALLER, mvc, post("/offers/" + offerId + "/review")
                .contentType(APPLICATION_JSON).content("{}"));
    }

    private String anOfferUnderEditing() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, QualityFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin()));
        offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START);
        return offerId;
    }

    private String aRemovedOffer() {
        String offerId = anOfferUnderEditing();
        offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA());
        offers.publish(offerId, Instant.now(), REVIEWER);
        offers.removeOffer(offerId);
        return offerId;
    }
}
