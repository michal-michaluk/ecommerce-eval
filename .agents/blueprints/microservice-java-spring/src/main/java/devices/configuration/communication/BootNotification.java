package devices.configuration.communication;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder(toBuilder = true)
@Schema(description = "Boot of a device stored from a protocol boot notification.")
public record BootNotification(
        @Schema(description = "Identifier of the device that booted.")
        String deviceId,
        @Schema(description = "Protocol the boot notification was received on.")
        Protocols protocol,
        @Schema(description = "Vendor of the device.")
        String vendor,
        @Schema(description = "Model of the device.")
        String model,
        @Schema(description = "Serial number of the device.")
        String serial,
        @Schema(description = "Firmware version of the device.")
        String firmware) {

    public BootNotification {
        if (deviceId == null || deviceId.isBlank()) {
            throw new CommunicationException(CommunicationException.Error.PROTOCOL_PAYLOAD_INCOMPLETE, "deviceId", protocol);
        }
    }

    @Schema(description = "Protocol carrying the boot notification.")
    public enum Protocols {
        @Schema(description = "IoT 2.0 boot notification.")
        IoT20,
        @Schema(description = "IoT 1.6 boot notification.")
        IoT16
    }
}
