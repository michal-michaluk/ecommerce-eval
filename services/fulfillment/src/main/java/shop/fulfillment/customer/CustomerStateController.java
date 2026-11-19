package shop.fulfillment.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 18's one route: the customer's view of one processing, served from the read model and from nothing else.
 *
 * <p>It is a resource of its own and not a filtered projection of the employee's snapshot (D-11-4): the scope that reaches it
 * is {@code fulfillment-customer}, the payload carries no assignee and no issue, and the handler holds no write model, so the
 * read path cannot drift into the aggregate ({@code http.no-write-model-in-read}).
 *
 * <p>No ownership check is made — the order carries no customer identity to compare against, and the mapping is out of scope
 * (register D13, element 18 Q-18-2). The scope alone decides who may ask.
 */
@RestController
@RequiredArgsConstructor
class CustomerStateController {

    private final CustomerStateProjection states;

    @GetMapping(path = "/processing/{processingId}/state", produces = APPLICATION_JSON_VALUE)
    CustomerState state(@PathVariable String processingId) {
        return states.findState(processingId);
    }
}
