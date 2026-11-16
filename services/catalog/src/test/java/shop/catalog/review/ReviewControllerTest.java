package shop.catalog.review;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shop.catalog.ApiAdviceFixture;
import shop.catalog.CallerFixture;
import shop.catalog.offer.Offer;
import shop.catalog.JsonAssert;
import shop.catalog.Scopes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static shop.catalog.review.ReviewCandidateFixture.TITLE;
import static shop.catalog.review.ReviewCandidateFixture.blockingFindings;
import static shop.catalog.review.ReviewCandidateFixture.content;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.OFFER_ID;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REVIEWER;

/**
 * 12's `review process` resource over the real {@link ReviewService} and its fakes: start the review (freeze, override), read it (frozen
 * candidate and findings), decide it (acceptance publishes and replays as a no-op, rejection needs a reason, only the requester
 * withdraws), and the three refusals the route answers with — `REVIEW_ALREADY_OPEN`, `REVIEW_NOT_OPEN` and `QUALITY_FINDINGS`.
 *
 * <p>Route scope is {@code catalog-author} to start and {@code catalog-author|reviewer} to read or decide; the decision each caller
 * owns inside the PATCH is {@link Scopes}' rule, so the escalation this test pins is the one the route rule cannot see: an author cannot
 * accept.
 */
class ReviewControllerTest {

    static final String REVIEW = "/offers/" + OFFER_ID + "/review";

    static final String OTHER_AUTHOR = "author.zielinski";

    final Map<String, ReviewProcess> reviews = new HashMap<>();

    final FakeOfferDraftSnapshot offers = FakeOfferDraftSnapshot.holding(content());

    final FakeOfferPublication publications = new FakeOfferPublication();

    MockMvc mvc;

    @BeforeEach
    void opensTheResource() {
        mvc = mvcWithReport(List.of());
    }

    @AfterEach
    void clearsTheCaller() {
        CallerFixture.forgets();
    }

    @Test
    void startsAReviewOfTheContentTheOfferHolds() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);

        MockHttpServletResponse created = mvc.perform(starts("{}")).andReturn().getResponse();

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(JsonPath.<String>read(created.getContentAsString(), "$.reviewId")).startsWith("RV-");
        JsonAssert.assertThat(created.getContentAsString()).hasFieldsLike("""
                { "offerId": "%s", "state": "requested", "requestedBy": "%s", "publishedOfferVersion": null,
                  "candidate": { "description": { "title": "%s" },
                                 "media": [ { "mediaId": "M-01", "primary": true },
                                            { "mediaId": "M-02", "primary": false },
                                            { "mediaId": "M-03", "primary": false } ],
                                 "availabilityStart": "2026-07-01" },
                  "findings": [], "override": null }
                """.formatted(OFFER_ID, AUTHOR, TITLE));
    }

    @Test
    void refusesASecondOpenReviewOfTheSameOffer() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse refused = mvc.perform(starts("{}")).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"REVIEW_ALREADY_OPEN\"}");
    }

    @Test
    void pushesAReviewPastTheGateWithAnOverrideTheAuthorRecords() throws Exception {
        mvc = mvcWithReport(blockingFindings());
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);

        MockHttpServletResponse created = mvc.perform(starts("""
                { "override": { "findings": [ "QP-2", "QP-6" ] } }
                """)).andReturn().getResponse();

        assertThat(created.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        JsonAssert.assertThat(created.getContentAsString()).hasFieldsLike("""
                { "state": "requested", "override": { "actor": "%s", "findings": [ "QP-2", "QP-6" ] } }
                """.formatted(AUTHOR));
    }

    @Test
    void refusesAnAcceptanceTheFrozenFindingsBlock() throws Exception {
        mvc = mvcWithReport(blockingFindings());
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{ \"override\": { \"findings\": [ \"QP-2\", \"QP-6\" ] } }"));

        MockHttpServletResponse refused = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, """
                { "decision": "accepted" }
                """)).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        JsonAssert.assertThat(refused.getContentAsString()).hasFieldsLike("""
                { "reason": "QUALITY_FINDINGS" }
                """);
        assertThat(refused.getContentAsString()).contains("\"rule\":\"QP-6\"", "\"severity\":\"BLOCKING\"");
    }

    @Test
    void answersAnOfferThatNeverAskedWith404AndNoBody() throws Exception {
        CallerFixture.actsAs(REVIEWER, Scopes.REVIEWER);

        MockHttpServletResponse missing = mvc.perform(get(REVIEW)).andReturn().getResponse();

        assertThat(missing.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(missing.getContentAsString()).isEmpty();
    }

    @Test
    void readsTheFrozenCandidateEvenAfterTheDraftChanged() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));
        offers.offer(OFFER_ID, ReviewCandidateFixture.editedContent());
        CallerFixture.actsAs(REVIEWER, Scopes.REVIEWER);

        MockHttpServletResponse read = mvc.perform(get(REVIEW)).andReturn().getResponse();

        assertThat(read.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(read.getContentAsString()).hasFieldsLike("""
                { "state": "requested", "candidate": { "description": { "title": "%s" } } }
                """.formatted(TITLE));
    }

    @Test
    void answers404ForAnOfferThatDoesNotExistAndForItsDecision() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);

        MockHttpServletResponse started = mvc.perform(starts("{}", "/offers/OFR-UNKNOWN/review")).andReturn().getResponse();
        MockHttpServletResponse decided = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }",
                "/offers/OFR-UNKNOWN/review")).andReturn().getResponse();

        assertThat(started.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(started.getContentAsString()).isEmpty();
        assertThat(decided.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(decided.getContentAsString()).isEmpty();
    }

    @Test
    void refusesAnAcceptanceOfARemovedOfferWithTheReasonOfTheState() throws Exception {
        offers.offer(OFFER_ID, Offer.State.REMOVED, ReviewCandidateFixture.content());
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse refused = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }"))
                .andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"OFFER_REMOVED\"}");
        assertThat(publications.publications()).isZero();
    }

    @Test
    void answersABlockedAcceptanceWithTheFindingsAndAReplayWithNothingToDecide() throws Exception {
        mvc = mvcWithReport(blockingFindings());
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{ \"override\": { \"findings\": [ \"QP-2\", \"QP-6\" ] } }"));
        mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }"));

        MockHttpServletResponse replay = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }"))
                .andReturn().getResponse();

        assertThat(replay.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(replay.getContentAsString()).isEqualTo("{\"reason\":\"REVIEW_NOT_OPEN\"}");
        assertThat(publications.publications()).isZero();
    }

    @Test
    void acceptancePublishesTheFrozenCandidateAsTheFirstVersion() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse decided = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, """
                { "decision": "accepted" }
                """)).andReturn().getResponse();

        assertThat(decided.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(decided.getContentAsString()).hasFieldsLike("""
                { "state": "accepted", "reviewedBy": "%s", "publishedOfferVersion": 1 }
                """.formatted(REVIEWER));
        assertThat(publications.publications()).isEqualTo(1);
    }

    @Test
    void replayingTheSameAcceptanceAnswersTheSamePublishedVersion() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));
        mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }"));

        MockHttpServletResponse replayed = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"accepted\" }")).andReturn().getResponse();

        assertThat(replayed.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(JsonPath.<Integer>read(replayed.getContentAsString(), "$.publishedOfferVersion")).isEqualTo(1);
        assertThat(publications.publications()).isEqualTo(1);
    }

    @Test
    void aRejectionNeedsAReason() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse refused = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"rejected\" }"))
                .andReturn().getResponse();
        MockHttpServletResponse rejected = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, """
                { "decision": "rejected", "reason": "%s" }
                """.formatted(REASON))).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonAssert.assertThat(refused.getContentAsString()).hasFieldsLike("""
                { "errors": [ { "field": "reasonGiven" } ] }
                """);
        assertThat(rejected.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(rejected.getContentAsString()).hasFieldsLike("""
                { "state": "rejected", "reason": "%s", "publishedOfferVersion": null }
                """.formatted(REASON));
    }

    @Test
    void onlyTheRequesterWithdrawsAndOnlyTheReviewerDecides() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse stranger = mvc.perform(decides(OTHER_AUTHOR, Scopes.AUTHOR, "{ \"decision\": \"withdrawn\" }"))
                .andReturn().getResponse();
        MockHttpServletResponse withdrawn = mvc.perform(decides(AUTHOR, Scopes.AUTHOR, "{ \"decision\": \"withdrawn\" }"))
                .andReturn().getResponse();

        assertThat(stranger.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(stranger.getContentAsString()).isEqualTo("{\"reason\":\"WITHDRAWAL_NEEDS_REQUESTER\"}");
        assertThat(withdrawn.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(withdrawn.getContentAsString()).contains("\"state\":\"withdrawn\"");
    }

    @Test
    void refusesAReviewersWithdrawalOfARequestTheyDidNotMake() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse refused = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"withdrawn\" }"))
                .andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"WITHDRAWAL_NEEDS_REQUESTER\"}");
    }

    @Test
    void refusesAnAuthorsAcceptanceWithTheScopeOfTheDecision() throws Exception {
        CallerFixture.actsAs(AUTHOR, Scopes.AUTHOR);
        mvc.perform(starts("{}"));

        MockHttpServletResponse refused = mvc.perform(decides(AUTHOR, Scopes.AUTHOR, "{ \"decision\": \"accepted\" }")).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
        assertThat(publications.publications()).isZero();
    }

    @Test
    void answersAnOfferWithNoReviewAndNoDecisionWithTheReasonOfTheState() throws Exception {
        CallerFixture.actsAs(REVIEWER, Scopes.REVIEWER);

        MockHttpServletResponse refused = mvc.perform(decides(REVIEWER, Scopes.REVIEWER, "{ \"decision\": \"rejected\", \"reason\": \"brak opisu\" }"))
                .andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"REVIEW_NOT_OPEN\"}");
    }

    private MockMvc mvcWithReport(List<ReviewFinding> findings) {
        ReviewService service = ReviewServiceFixture.service(new InMemoryReviewRepository(), offers, publications,
                FakeQualityFindings.answeringWith(findings), ReviewFixture.OVERRIDE_ENABLED);
        return MockMvcBuilders.standaloneSetup(new ReviewController(service))
                .setControllerAdvice(ApiAdviceFixture.apiErrorAdvice())
                .build();
    }

    private static MockHttpServletRequestBuilder starts(String body) {
        return starts(body, REVIEW);
    }

    private static MockHttpServletRequestBuilder starts(String body, String path) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder decides(String caller, String scope, String body) {
        return decides(caller, scope, body, REVIEW);
    }

    private MockHttpServletRequestBuilder decides(String caller, String scope, String body, String path) {
        CallerFixture.actsAs(caller, scope);
        return patch(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    class InMemoryReviewRepository implements ReviewRepository {

        @Override
        public Optional<ReviewProcess> get(String reviewId) {
            return Optional.ofNullable(reviews.get(reviewId));
        }

        @Override
        public List<Review> reviewsOf(String offerId) {
            return reviews.values().stream()
                    .map(ReviewProcess::toReview)
                    .filter(review -> review.offerId().equals(offerId))
                    .toList();
        }

        @Override
        public void save(ReviewProcess review) {
            reviews.put(review.reviewId, review);
        }
    }
}
