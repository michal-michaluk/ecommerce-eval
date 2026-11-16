package shop.catalog.reads;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's `pending reviews` collection: the one read that is not about a single offer, served from the read model of 15 and from nothing
 * else (`http.no-write-model-in-read`).
 *
 * <p>It therefore lives beside the read model it answers with, the way {@code http-adapter.md} puts a read controller in the package of
 * its {@code *Projection}: the review context owns the process, this context owns the list a reviewer triages.
 *
 * <p>The list excludes the caller's own requests in the query itself (15 RULE-4) — a reviewer must not see a review they cannot decide —
 * and {@code waitingFor} is computed per render (15 RULE-1). Paging is D16's: both parameters are required and every value outside
 * {@code page >= 0} and {@code 1 <= size <= 100} is the request's own `400 {errors[{field}]}`, never a silently clamped page.
 */
@RestController
@RequiredArgsConstructor
class ReviewsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PendingReviewsProjection reviews;

    @GetMapping(path = "/reviews", produces = APPLICATION_JSON_VALUE)
    PendingReviewsPage pendingReviews(@RequestParam @Min(0) int page,
                                     @RequestParam @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PendingReviewsPage.of(reviews.findAllPending(caller(), PageRequest.of(page, size)));
    }

    /** The reviewer the token authenticates as; the query drops their own requests before this adapter sees a row. */
    private static String caller() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** One page of 15's list: the rows and the page they were cut from. */
    record PendingReviewsPage(List<PendingReviewsReads> items, Paging page) {

        static PendingReviewsPage of(org.springframework.data.domain.Page<PendingReviewsReads> page) {
            return new PendingReviewsPage(page.getContent(),
                    new Paging(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
        }

        /** Where the rows came from, so a client can ask for the next page without counting them. */
        record Paging(int number, int size, long totalElements, int totalPages) {
        }
    }
}
