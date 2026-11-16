package devices.configuration.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import static java.util.Optional.ofNullable;

@Builder(toBuilder = true)
@Schema(description = "Feature flags of a device. A field left out of a request keeps its current value.")
record Settings(
        @Schema(description = "Whether the device may start a charging session automatically.")
        Boolean autoStart,
        @Schema(description = "Whether the device can be controlled remotely.")
        Boolean remoteControl,
        @Schema(description = "Whether charging at the device is billed.")
        Boolean billing,
        @Schema(description = "Whether charging at the device is reimbursed.")
        Boolean reimbursement,
        @Schema(description = "Whether the device is shown on the map.")
        Boolean showOnMap,
        @Schema(description = "Whether the device is publicly accessible.")
        Boolean publicAccess) {

    static Settings defaultSettings() {
        return builder()
                .autoStart(false)
                .remoteControl(false)
                .billing(false)
                .reimbursement(false)
                .showOnMap(false)
                .publicAccess(false)
                .build();
    }

    public boolean isAutoStart() {
        return autoStart != null && autoStart;
    }

    public boolean isRemoteControl() {
        return remoteControl != null && remoteControl;
    }

    public boolean isBilling() {
        return billing != null && billing;
    }

    public boolean isReimbursement() {
        return reimbursement != null && reimbursement;
    }

    public boolean isShowOnMap() {
        return showOnMap != null && showOnMap;
    }

    public boolean isPublicAccess() {
        return publicAccess != null && publicAccess;
    }

    public Settings merge(Settings other) {
        SettingsBuilder merged = this.toBuilder();
        ofNullable(other.autoStart).ifPresent(merged::autoStart);
        ofNullable(other.remoteControl).ifPresent(merged::remoteControl);
        ofNullable(other.billing).ifPresent(merged::billing);
        ofNullable(other.reimbursement).ifPresent(merged::reimbursement);
        ofNullable(other.showOnMap).ifPresent(merged::showOnMap);
        ofNullable(other.publicAccess).ifPresent(merged::publicAccess);
        return merged.build();
    }
}
