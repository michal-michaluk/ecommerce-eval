package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Configuration rules the device violates; the device is valid when all are false.")
record Violations(
        @Schema(description = "No operator is assigned.")
        boolean operatorNotAssigned,
        @Schema(description = "No provider is assigned.")
        boolean providerNotAssigned,
        @Schema(description = "No location is set.")
        boolean locationMissing,
        @Schema(description = "The device is shown on the map but has no location.")
        boolean showOnMapButMissingLocation,
        @Schema(description = "The device is shown on the map but is not publicly accessible.")
        boolean showOnMapButNoPublicAccess) {

    boolean isValid() {
        return !operatorNotAssigned
                && !providerNotAssigned
                && !locationMissing
                && !showOnMapButMissingLocation
                && !showOnMapButNoPublicAccess;
    }
}
