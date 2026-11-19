package shop.fulfillment.processing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * The two commands of the picking screen (elements 02 and 16): scan one position, then put the picked positions into the
 * box.
 *
 * <p>Both answer {@code 200} with the snapshot of element 11 — a repeated scan and a repeated pack are the no-ops of D-11-2
 * and need no second status — and both are refusals raised by the aggregate, never by this adapter: an order on hold
 * answers {@code 409 ON_HOLD}, a position that is not picked answers {@code 409 POSITION_NOT_PICKED}, and a caller who does
 * not hold the order answers {@code 409 ACTOR_NOT_ALLOWED} through the one advice. The caller is the token's subject, never
 * a body value (element 16: only the assigned employee confirms and packs).
 */
@RestController
@RequiredArgsConstructor
class ProcessingCommandController {

    private final ProcessingService processings;

    @PostMapping(path = "/processing/{processingId}/positions/{productNumber}",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    ProcessingSnapshot scan(@PathVariable String processingId, @PathVariable String productNumber,
                            @RequestBody @Valid ScanPosition scan) {
        return ProcessingSnapshot.of(processings.pick(processingId, productNumber, caller()));
    }

    @PostMapping(path = "/processing/{processingId}/packing",
            consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    ProcessingSnapshot pack(@PathVariable String processingId, @RequestBody @Valid PackPositions pack) {
        return ProcessingSnapshot.of(processings.pack(processingId, pack.productNumbers(), caller()));
    }

    /** The employee the token authenticates as; who may confirm is not the caller's choice (element 16). */
    private static String caller() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
