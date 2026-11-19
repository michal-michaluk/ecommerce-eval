package shop.fulfillment.processing;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 07's one route: an employee claims one order, and leaves with the claim or with the refusal.
 *
 * <p>A granted claim is {@code 201} — a claim is a fact that did not exist before, and one claim emits one event (RULE-8). A
 * repeated claim by the same employee answers {@code 200} with the same state (element 11 D-11-2). The refusal is not built
 * here: the aggregate raises it and the one advice answers {@code 409} with the guard's own reason.
 *
 * <p>The request takes no body on purpose — the claimer is the assignee, so the assignee is the caller's own identity read
 * from the token (RULE-5). The empty command record keeps the route a command and keeps the body optional.
 */
@RestController
@RequiredArgsConstructor
class AssignmentController {

    private final AssignmentService assignments;

    @PostMapping(path = "/processing/{processingId}/assignment", produces = APPLICATION_JSON_VALUE)
    OrderAssignmentView claim(@PathVariable String processingId,
                              @RequestBody(required = false) @Valid AssignOrder command,
                              HttpServletResponse response) {
        OrderClaim claim = assignments.claim(processingId, caller());
        response.setStatus(claim.granted() ? HttpStatus.CREATED.value() : HttpStatus.OK.value());
        return OrderAssignmentView.of(claim.processing());
    }

    /** The employee the token authenticates as; what a claim may name is not the caller's choice. */
    private static String caller() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** The claim's answer: the state the order is in and who holds it. */
    record OrderAssignmentView(String processingId, String state, String assignee) {

        static OrderAssignmentView of(OrderProcessing processing) {
            return new OrderAssignmentView(processing.processingId().value(), processing.state().wireName(),
                    processing.assignee());
        }
    }
}
