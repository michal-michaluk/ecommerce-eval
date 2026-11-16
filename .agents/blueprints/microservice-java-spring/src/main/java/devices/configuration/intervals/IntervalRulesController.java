package devices.configuration.intervals;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@Tag(name = "Interval rules", description = "Heartbeat interval rules used for boot notifications.")
class IntervalRulesController {

    private final IntervalRulesDocumentRepository intervalRules;

    @GetMapping(path = "/configs/interval-rules",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getIntervalRules",
            summary = "Get interval rules",
            description = "Returns the stored interval rules, or the default rules when none are stored.")
    @SecurityRequirement(name = "bearerAuth")
    HartbitIntervalCalculation get() {
        return intervalRules.get();
    }

    @PutMapping(path = "/configs/interval-rules",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "putIntervalRules",
            summary = "Replace interval rules",
            description = "Stores the given interval rules, replacing the previous ones.")
    @SecurityRequirement(name = "bearerAuth")
    void put(@RequestBody @Valid HartbitIntervalCalculation configuration) {
        intervalRules.save(configuration);
    }
}
