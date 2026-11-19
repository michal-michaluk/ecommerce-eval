package shop.fulfillment.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 10's one route: the walk an assigned employee performs, served from the query handler and from nothing else.
 *
 * <p>The handler holds the projection and no write model, so the read path cannot drift into the aggregate
 * ({@code http.no-write-model-in-read}). The list is generated per request — it is a snapshot, and no source says it is
 * regenerated under a walking employee (element 10 DEFAULT-3).
 */
@RestController
@RequiredArgsConstructor
class PickListController {

    private final PickListProjection pickLists;

    @GetMapping(path = "/processing/{processingId}/pick-list", produces = APPLICATION_JSON_VALUE)
    PickList pickList(@PathVariable String processingId) {
        return pickLists.findPickList(processingId);
    }
}
