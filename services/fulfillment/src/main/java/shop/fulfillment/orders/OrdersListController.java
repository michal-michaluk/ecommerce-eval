package shop.fulfillment.orders;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 15's one route: the employee's current orders, served from the read model and from nothing else.
 *
 * <p>The handler holds the projection and no write model, so the read path cannot drift into the aggregate
 * ({@code http.no-write-model-in-read}). The contract of element 11 asserts no page on this collection and element 15 says the
 * list has no pagination, so the route takes no paging parameters and answers {@code items} alone; a page would be a bound no
 * source names (element 11 Q-11-1).
 */
@RestController
@RequiredArgsConstructor
class OrdersListController {

    private final OrdersListProjection orders;

    @GetMapping(path = "/orders", produces = APPLICATION_JSON_VALUE)
    OrdersPage list() {
        return new OrdersPage(orders.findAllCurrent());
    }

    /** The collection of element 12's list: its rows are the whole answer, and every row is a current order. */
    record OrdersPage(List<OrdersListEntry> items) {
    }
}
