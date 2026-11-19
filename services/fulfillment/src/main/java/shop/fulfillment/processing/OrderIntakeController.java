package shop.fulfillment.processing;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 12's one route: the finished order arrives from checkout and leaves as a processing.
 *
 * <p>The handler validates the command, calls {@link OrderIntakeService} once and maps the answer back — {@code 201} for the
 * delivery that opened the processing, {@code 200} for a repeated {@code orderHash}, because idempotency is stated in the
 * answer rather than as a second resource or a second error (element 11 D-11-2). A refused order never reaches this adapter:
 * {@code Order} raises the refusal and the one advice answers {@code 422 ORDER_INCOMPLETE}.
 */
@RestController
@RequiredArgsConstructor
class OrderIntakeController {

    private final OrderIntakeService orders;

    @PostMapping(path = "/orders", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    OrderIntakeView accept(@RequestBody @Valid OrderHandover handover, HttpServletResponse response) {
        OrderAcceptance acceptance = orders.accept(handover);
        response.setStatus(acceptance.duplicate() ? HttpStatus.OK.value() : HttpStatus.CREATED.value());
        return OrderIntakeView.of(acceptance);
    }

    /**
     * The intake's answer: the identity the caller addresses the processing by, the state it opens in, and — only for a
     * duplicate delivery — the fact that nothing was created.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OrderIntakeView(String processingId, String state, Boolean duplicate) {

        static OrderIntakeView of(OrderAcceptance acceptance) {
            OrderProcessing processing = acceptance.processing();
            return new OrderIntakeView(processing.processingId().value(), processing.state().wireName(),
                    acceptance.duplicate() ? Boolean.TRUE : null);
        }
    }
}
