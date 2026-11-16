package shop.catalog.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.catalog.CreatedResource;
import shop.catalog.Scopes;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's `review process` resource: start it, read it, decide it — one route per verb, over the review context's primary port.
 *
 * <p>The three handlers are 12's three intents and nothing else. `POST` starts the review and freezes the candidate (04 RULE-3); its
 * body carries the override and only the override, because a push past the gate is the author's and belongs to the request (RULE-8,
 * D27). `GET` answers the review as it stands, the frozen candidate included, and a review that is not open is the resource's `404`.
 * `PATCH` carries the decision: {@code accepted | rejected | withdrawn} plus the reason a rejection needs — acceptance never carries an
 * override, and which decision the caller may take is {@link Scopes}' rule, not this adapter's (D27, F15, D50).
 *
 * <p>The caller is the JWT's subject: it is the requester of a review and the reviewer of a decision, and the aggregate refuses a
 * reviewer who is the requester (RULE-2) or a withdrawal by anybody else (RULE-6).
 */
@RestController
@RequiredArgsConstructor
class ReviewController {

    private final ReviewService reviews;

    @PostMapping(path = "/offers/{offerId}/review",
            consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CreatedReview requestReview(@PathVariable String offerId, @RequestBody @Valid RequestReview request) {
        return CreatedReview.of(ServedReview.of(reviews.requestFor(offerId, caller(), request.overriddenRules())));
    }

    @GetMapping(path = "/offers/{offerId}/review", produces = APPLICATION_JSON_VALUE)
    ServedReview review(@PathVariable String offerId) {
        return reviews.latestReviewOf(offerId)
                .map(ServedReview::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping(path = "/offers/{offerId}/review",
            consumes = APPLICATION_JSON_VALUE,
            produces = APPLICATION_JSON_VALUE)
    ServedReview decideReview(@PathVariable String offerId, @RequestBody @Valid DecideReview decide) {
        Scopes.requireReviewDecision(decide.decision().wire());
        Review open = reviews.reviewToDecide(offerId);
        return ServedReview.of(decided(decide, open.reviewId(), caller()));
    }

    private Review decided(DecideReview decide, String reviewId, String actor) {
        Review decided = switch (decide.decision()) {
            case ACCEPTED -> reviews.decide(reviewId, actor).orElseThrow(() -> ReviewException.notOpen(reviewId));
            case REJECTED -> reviews.reject(reviewId, actor, decide.reason()).orElseThrow(() -> ReviewException.notOpen(reviewId));
            case WITHDRAWN -> reviews.withdraw(reviewId, actor).orElseThrow(() -> ReviewException.notOpen(reviewId));
        };
        if (decided.state() == Review.State.ACCEPTED && !decided.isPublished()) {
            throw ReviewException.blockedAcceptanceOf(decided);
        }
        return decided;
    }

    private static String caller() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * The command 12 starts a review with: the override, and nothing else (D27). Absent means "no push", which is the ordinary request;
     * present but naming fewer findings than 08 reported is 04 RULE-8's refusal, decided by the aggregate and not here.
     */
    record RequestReview(@Valid Override override) {

        List<String> overriddenRules() {
            return override == null ? List.of() : Objects.requireNonNullElse(override.findings(), List.of());
        }

        /** The findings the author takes responsibility for, by the label 08 published. */
        record Override(List<String> findings) {
        }
    }

    /** The command 12 decides a review with: the decision and the reason a rejection needs (04 RULE-5). */
    record DecideReview(@NotNull Decision decision, String reason) {

        /** A rejection without a reason is an invalid command record, which is 12's `400`, not a refused command. */
        @AssertTrue(message = "a rejection needs a reason")
        boolean isReasonGiven() {
            return decision != Decision.REJECTED || (reason != null && !reason.isBlank());
        }

        /** The three exits of one review, as 12 spells them in the body. */
        enum Decision {
            ACCEPTED,
            REJECTED,
            WITHDRAWN;

            @JsonValue
            String wire() {
                return name().toLowerCase(Locale.ROOT);
            }

            @JsonCreator
            static Decision of(String wire) {
                return valueOf(wire.toUpperCase(Locale.ROOT));
            }
        }
    }

    /** The review resource as 12 answers it: the process, the frozen candidate, the report and the override that pushed it. */
    record ServedReview(String reviewId, String offerId, String state, String requestedBy, Instant requestedAt,
                        String reviewedBy, Instant reviewedAt, String reason, Integer publishedOfferVersion,
                        Candidate candidate, List<ReviewFinding> findings, Override override) {

        static ServedReview of(Review review) {
            ReviewCandidate candidate = review.candidate();
            return new ServedReview(review.reviewId(), review.offerId(), wire(review.state()), review.requestedBy(),
                    review.requestedAt(), review.reviewedBy(), review.reviewedAt(), review.reason(),
                    review.publishedOfferVersion(), Candidate.of(candidate), candidate.findings(),
                    Override.of(candidate.override()));
        }

        private static String wire(Review.State state) {
            return state.name().toLowerCase(Locale.ROOT);
        }

        /** What is being reviewed, frozen at the request (RULE-3): never the offer's live draft. */
        record Candidate(shop.catalog.offer.ProductDescription description, List<shop.catalog.offer.Media> media,
                         java.time.LocalDate availabilityStart) {

            static Candidate of(ReviewCandidate candidate) {
                return new Candidate(candidate.description(), candidate.media(), candidate.availabilityStart());
            }
        }

        /** The author's push past the gate (RULE-8): who recorded it, when, and the findings it takes responsibility for. */
        record Override(String actor, Instant recordedAt, List<String> findings) {

            static Override of(ReviewOverride override) {
                return override == null ? null
                        : new Override(override.actor(), override.recordedAt(),
                                override.ignoredFindings().stream().map(ReviewFinding::rule).toList());
            }
        }
    }

    /** The same body, plus the only thing a create adds: where the review it started now lives (D19a). */
    record CreatedReview(@JsonValue ServedReview review) implements CreatedResource {

        static CreatedReview of(ServedReview review) {
            return new CreatedReview(review);
        }

        @Override
        public String location() {
            return "/offers/" + review.offerId() + "/review";
        }
    }
}
