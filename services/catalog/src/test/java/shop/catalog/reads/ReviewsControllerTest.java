package shop.catalog.reads;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shop.catalog.ApiAdviceFixture;
import shop.catalog.CallerFixture;
import shop.catalog.JsonAssert;
import shop.catalog.Scopes;
import shop.catalog.review.Review;
import shop.catalog.review.ReviewCandidate;
import shop.catalog.review.ReviewCandidateFixture;
import shop.catalog.review.ReviewFinding;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 12's `pending reviews` collection over the real {@link PendingReviewsProjection} and the read model of 15: the rows the reviewer
 * triages, `findingsCount` and the rendered `waitingFor`, the page they were cut from, and D16's paging as the request's own `400`.
 *
 * <p>The query is the projection's, so the two facts this adapter is responsible for are the ones asserted here: the caller is handed
 * to it (15 RULE-4's exclusion is the query's own filter) and nothing outside {@code page >= 0}, {@code 1 <= size <= 100} reaches it.
 */
class ReviewsControllerTest {

    static final String REVIEWER = "reviewer.kowalska";

    static final String AUTHOR = "author.nowak";

    static final Instant NOW = Instant.parse("2025-06-15T09:10:00Z");

    static final Instant REQUESTED_AT = NOW.minusSeconds(16 * 3600 + 30 * 60);

    final PendingReviewsProjection.PendingReviews rows = mock(PendingReviewsProjection.PendingReviews.class);

    final PendingReviewsProjection projection = new PendingReviewsProjection(rows, Clock.fixed(NOW, ZoneOffset.UTC));

    MockMvc mvc;

    @BeforeEach
    void opensTheCollection() {
        mvc = MockMvcBuilders.standaloneSetup(new ReviewsController(projection))
                .setControllerAdvice(ApiAdviceFixture.apiErrorAdvice())
                .build();
        CallerFixture.actsAs(REVIEWER, Scopes.REVIEWER);
        givenPendingRows(onePendingReview());
    }

    @AfterEach
    void clearsTheCaller() {
        CallerFixture.forgets();
    }

    @Test
    void listsThePendingReviewsWithTheirFindingsCountAndWaitingTime() throws Exception {
        MockHttpServletResponse listed = mvc.perform(get("/reviews?page=0&size=20")).andReturn().getResponse();

        assertThat(listed.getStatus()).isEqualTo(200);
        JsonAssert.assertThat(listed.getContentAsString()).hasFieldsLike("""
                { "items": [ { "reviewId": "RV-12", "offerId": "OFR-75423", "title": "%s", "requestedBy": "%s",
                               "waitingFor": "PT16H30M", "findingsCount": 2 } ],
                  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 } }
                """.formatted(ReviewCandidateFixture.TITLE, AUTHOR));
    }

    @Test
    void asksTheQueryForTheCallersPendingReviewsOnly() throws Exception {
        mvc.perform(get("/reviews?page=0&size=20"));

        verify(rows).findAllByStateAndRequestedByNotOrderByRequestedAtAsc(
                Review.State.REQUESTED, REVIEWER, PageRequest.of(0, 20));
    }

    @Test
    void refusesAMissingPagingParameter() throws Exception {
        MockHttpServletResponse refused = mvc.perform(get("/reviews")).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(400);
        assertThat(refused.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"page\"}]}");
    }

    @Test
    void refusesAPageSizeBeyondTheBoundOfD16() throws Exception {
        MockHttpServletResponse tooWide = mvc.perform(get("/reviews?page=0&size=101")).andReturn().getResponse();
        MockHttpServletResponse noRoom = mvc.perform(get("/reviews?page=0&size=0")).andReturn().getResponse();

        assertThat(tooWide.getStatus()).isEqualTo(400);
        assertThat(tooWide.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"size\"}]}");
        assertThat(noRoom.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"size\"}]}");
    }

    @Test
    void refusesANegativePage() throws Exception {
        MockHttpServletResponse refused = mvc.perform(get("/reviews?page=-1&size=20")).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(400);
        assertThat(refused.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"page\"}]}");
    }

    private void givenPendingRows(Review review) {
        PendingReviewsProjection.PendingReviewRow row = new PendingReviewsProjection.PendingReviewRow(review.reviewId());
        given(rows.findAllByStateAndRequestedByNotOrderByRequestedAtAsc(
                eq(Review.State.REQUESTED), anyString(), any(Pageable.class)))
                .willAnswer(invocation -> PendingReviewsFixture.pageOf(row.copyOf(review), invocation.getArgument(2)));
    }

    private static Review onePendingReview() {
        return PendingReviewsFixture.pendingReview("RV-12", "OFR-75423", AUTHOR, REQUESTED_AT,
                ReviewCandidateFixture.blockingFindings());
    }

    /** The reviews 15's list is cut from, built through one factory so no test calls a wide constructor. */
    public static class PendingReviewsFixture {

        private PendingReviewsFixture() {
        }

        public static Review pendingReview(
                String reviewId, String offerId, String requestedBy, Instant requestedAt, List<ReviewFinding> findings) {
            ReviewCandidate candidate = new ReviewCandidate(ReviewCandidateFixture.descriptionOfInstanceA(),
                    ReviewCandidateFixture.mediaOfInstanceA(), ReviewCandidateFixture.AVAILABILITY_START, findings, null);
            return new Review(reviewId, offerId, Review.State.REQUESTED, requestedBy, requestedAt, candidate,
                    null, null, null, null);
        }

        static Page<PendingReviewsProjection.PendingReviewRow> pageOf(
                PendingReviewsProjection.PendingReviewRow row, Pageable pageable) {
            return new PageImpl<>(List.of(row), PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()), 1);
        }
    }
}
