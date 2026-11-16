package devices.configuration.communication.protocols.iot16;

import devices.configuration.communication.BootNotification;
import devices.configuration.communication.CommunicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "IoT 1.6 protocol", description = "Boot notification endpoint of the IoT 1.6 protocol. Public, no bearer token.")
class IoT16Controller {

    private final CommunicationService service;

    @PostMapping(path = "/protocols/iot16/bootnotification/{deviceId}",
            consumes = "application/json", produces = "application/json")
    @Operation(operationId = "postIot16BootNotification",
            summary = "Report an IoT 1.6 boot notification",
            description = "Registers the boot of the device and returns the server time, the heartbeat "
                          + "interval the device must use, and the state of the device.")
    BootNotificationResponse handleBootNotification(@PathVariable String deviceId,
                                                    @RequestBody @Valid BootNotificationRequest request) {
        return service.handleBoot(request.toBootNotificationEvent(deviceId))
                .map(resp -> BootNotificationResponse.builder()
                        .currentTime(resp.serverTime().toString())
                        .interval(resp.intervalInSeconds())
                        .status(resp.state(state -> switch (state) {
                                    case UNKNOWN -> BootNotificationResponse.Status.Rejected;
                                    case EXISTING -> BootNotificationResponse.Status.Accepted;
                                })
                        ).build()
                );
    }

    @Builder
    @Schema(description = "Boot notification of a charge point in the IoT 1.6 protocol.")
    record BootNotificationRequest(
            @Schema(description = "Vendor of the charge point; stored as the device vendor.")
            String chargePointVendor,
            @Schema(description = "Model of the charge point; stored as the device model.")
            String chargePointModel,
            @Schema(description = "Serial number of the charge point; not part of the stored boot notification.")
            String chargePointSerialNumber,
            @Schema(description = "Serial number of the charge box; stored as the device serial number.")
            String chargeBoxSerialNumber,
            @Schema(description = "Installed firmware version; stored as the device firmware.")
            String firmwareVersion,
            @Schema(description = "Integrated circuit card identifier of the SIM card; not part of the stored boot notification.")
            String iccid,
            @Schema(description = "International mobile subscriber identity of the SIM card; not part of the stored boot notification.")
            String imsi,
            @Schema(description = "Type of the meter; not part of the stored boot notification.")
            String meterType,
            @Schema(description = "Serial number of the meter; not part of the stored boot notification.")
            String meterSerialNumber) {

        BootNotification toBootNotificationEvent(String deviceId) {
            return new BootNotification(
                    deviceId, BootNotification.Protocols.IoT16,
                    chargePointVendor, chargePointModel,
                    chargeBoxSerialNumber, firmwareVersion
            );
        }
    }

    @Builder
    @Schema(description = "Answer to an IoT 1.6 boot notification.")
    record BootNotificationResponse(
            @Schema(description = "Server time when the boot notification was handled, ISO-8601.")
            String currentTime,
            @Schema(description = "Heartbeat interval the charge point must use, in seconds.")
            int interval,
            @Schema(description = "State of the device: `Accepted` when it is known, `Rejected` when it is not.")
            Status status) {

        @Schema(description = "State of the device after the boot notification; `Pending` is not returned.")
        enum Status {
            Accepted,
            Pending,
            Rejected
        }
    }
}
