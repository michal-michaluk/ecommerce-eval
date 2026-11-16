package devices.configuration.communication.protocols.iot20;

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
@Tag(name = "IoT 2.0 protocol", description = "Boot notification endpoint of the IoT 2.0 protocol. Public, no bearer token.")
class IoT20Controller {

    private final CommunicationService service;

    @PostMapping(path = "/protocols/iot20/bootnotification/{deviceId}",
            consumes = "application/json", produces = "application/json")
    @Operation(operationId = "postIot20BootNotification",
            summary = "Report an IoT 2.0 boot notification",
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

    @Schema(description = "Boot notification of a device in the IoT 2.0 protocol.")
    record BootNotificationRequest(
            @Schema(description = "Device reporting the boot notification.")
            Device device,
            @Schema(description = "Reason the device reports for the boot notification; not stored.")
            Reason reason) {

        @Builder
        @Schema(description = "Device reporting the boot notification.")
        record Device(
                @Schema(description = "Serial number of the device; stored as the device serial number.")
                String serialNumber,
                @Schema(description = "Model of the device; stored as the device model.")
                String model,
                @Schema(description = "Modem installed in the device; not part of the stored boot notification.")
                Modem modem,
                @Schema(description = "Vendor of the device; stored as the device vendor.")
                String vendorName,
                @Schema(description = "Installed firmware version; stored as the device firmware.")
                String firmwareVersion) {
        }

        @Schema(description = "Modem installed in the device.")
        record Modem(
                @Schema(description = "Integrated circuit card identifier of the SIM card.")
                String iccid,
                @Schema(description = "International mobile subscriber identity of the SIM card.")
                String imsi) {
        }

        @Schema(description = "Reason a device reports in a boot notification.")
        enum Reason {
            ApplicationReset,
            FirmwareUpdate,
            LocalReset,
            PowerUp,
            RemoteReset,
            ScheduledReset,
            Triggered,
            Unknown,
            Watchdog
        }

        BootNotification toBootNotificationEvent(String deviceId) {
            return new BootNotification(
                    deviceId, BootNotification.Protocols.IoT20,
                    device.vendorName, device.model,
                    device.serialNumber, device.firmwareVersion
            );
        }
    }

    @Builder
    @Schema(description = "Answer to an IoT 2.0 boot notification.")
    record BootNotificationResponse(
            @Schema(description = "Server time when the boot notification was handled, ISO-8601.")
            String currentTime,
            @Schema(description = "Heartbeat interval the device must use, in seconds.")
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
