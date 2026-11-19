package shop.fulfillment.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The state read of element 11's processing resource: the one payload every command of the resource answers, served for
 * whoever opens the order.
 *
 * <p>Both the employee and the resolver scope reach it (element 11's auth matrix): a resolver deciding a resume has to see
 * what is held, and the payload carries no customer-facing field the customer scope could not see — that scope has its own
 * resource (element 18, D-11-4).
 */
@RestController
@RequiredArgsConstructor
class ProcessingSnapshotController {

    private final ProcessingSnapshotProjection processings;

    @GetMapping(path = "/processing/{processingId}", produces = APPLICATION_JSON_VALUE)
    ProcessingSnapshot processing(@PathVariable String processingId) {
        return processings.findProcessing(processingId);
    }
}
