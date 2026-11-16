package devices.configuration.device;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Operator and provider assignment of a device; both are set together or not at all.")
public record Ownership(
        @Schema(description = "Operator the device is assigned to.")
        String operator,
        @Schema(description = "Provider the device is assigned to.")
        String provider) {

    public static Ownership unowned() {
        return new Ownership(null, null);
    }

    public static Ownership of(String operator, String provider) {
        return new Ownership(operator, provider);
    }

    public Ownership {
        boolean unowned = operator == null && provider == null;
        boolean owned = operator != null && provider != null;
        if (!unowned && !owned) {
            throw new DeviceException(DeviceException.Error.OWNERSHIP_INCOMPLETE, operator, provider);
        }
    }

    @JsonIgnore
    public boolean isUnowned() {
        return operator == null && provider == null;
    }

    @JsonIgnore
    public boolean isOwned() {
        return operator != null && provider != null;
    }
}
