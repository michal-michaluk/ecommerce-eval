package shop.fulfillment.processing;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Element 16's read surface: the one screen an assigned employee works from.
 *
 * <p>The route is this service's own — element 11 fixes the paths of the processing resource, the pick list and the
 * snapshot, and names none for the workstation, whose payload is the two regions of W1 composed (recorded in
 * {@code docs/phase-1-decisions.md}). It is a read: nothing on this screen writes, and the actions of the other views'
 * actors — readiness, issues, resume — are not reachable from it.
 */
@RestController
@RequiredArgsConstructor
class PickWorkstationController {

    private final PickWorkstationProjection workstations;

    @GetMapping(path = "/processing/{processingId}/pick-workstation", produces = APPLICATION_JSON_VALUE)
    PickWorkstation workstation(@PathVariable String processingId) {
        return workstations.findWorkstation(processingId);
    }
}
