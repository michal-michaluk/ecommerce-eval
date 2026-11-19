package shop.fulfillment.issues;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 17's one route: the open warehouse-reality gaps, served from the read model and from nothing else.
 *
 * <p>It is the resolver's screen and it has no other verb — no resolve, no close, no dismiss, no acknowledge. Closing an issue
 * is a fact of the resume command on the processing resource (registers D7, D8), so this class holds the projection and no
 * write model at all, and a route that would close one cannot be added here without adding a command no source names.
 *
 * <p>The collection is unpaginated like the orders list: one {@code items} envelope, no parameter (element 11 D-11-5).
 */
@RestController
@RequiredArgsConstructor
class IssueDashboardController {

    private final IssueDashboardProjection dashboard;

    @GetMapping(path = "/issues", produces = APPLICATION_JSON_VALUE)
    IssuesPage list() {
        return new IssuesPage(dashboard.findOpen());
    }

    /** The collection of element 17: its rows are the whole answer, and every row is an open issue. */
    record IssuesPage(List<IssueDashboardEntry> items) {
    }
}
