package devices.configuration.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@Tag(name = "Device reads", description = "Read models of devices.")
class DeviceReadsController {

    private final ReadModelsProjection reads;

    @GetMapping(path = "/devices", params = {"page", "size"},
            produces = "application/vnd.device.summary+json")
    @Operation(operationId = "listDevices",
            summary = "List devices",
            description = "Returns a page of the devices of one provider. The operation is content-negotiated "
                          + "across two representations: application/vnd.device.summary+json answers with a paged "
                          + "list of device summaries, and application/vnd.device.pin+json with the coordinates and "
                          + "normalised statuses of every device that has a location. Devices without a location are "
                          + "omitted from both representations.")
    @SecurityRequirement(name = "bearerAuth")
    Page<DeviceSummary> getSummary(
            @Parameter(description = "Identifier of the provider whose devices are listed.")
            String provider,
            @ParameterObject Pageable pageable) {
        return reads.findAllSummary(provider, pageable);
    }

    @GetMapping(path = "/devices", params = {"page", "size"},
            produces = "application/vnd.device.pin+json")
    @Operation(operationId = "listDevices",
            summary = "List devices",
            description = "Returns a page of the devices of one provider. The operation is content-negotiated "
                          + "across two representations: application/vnd.device.summary+json answers with a paged "
                          + "list of device summaries, and application/vnd.device.pin+json with the coordinates and "
                          + "normalised statuses of every device that has a location. Devices without a location are "
                          + "omitted from both representations.")
    @SecurityRequirement(name = "bearerAuth")
    List<DevicePin> getPins(
            @Parameter(description = "Identifier of the provider whose devices are listed.")
            String provider) {
        return reads.findAllPins(provider);
    }

    @GetMapping(path = "/devices/{deviceId}",
            produces = APPLICATION_JSON_VALUE)
    @Operation(operationId = "getDeviceDetails",
            summary = "Get device details",
            description = "Returns the stored configuration and the last boot notification of the device.")
    @SecurityRequirement(name = "bearerAuth")
    Optional<DeviceDetails> getDetails(@PathVariable String deviceId) {
        return reads.findById(deviceId);
    }
}
