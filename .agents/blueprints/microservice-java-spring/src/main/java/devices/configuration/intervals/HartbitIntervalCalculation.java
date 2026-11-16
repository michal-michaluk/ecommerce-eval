package devices.configuration.intervals;

import devices.configuration.communication.BootNotification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Schema(description = "Heartbeat interval rules: the first rule matching a boot notification decides, "
                      + "otherwise `defSeconds` applies.")
record HartbitIntervalCalculation(
        @Schema(description = "Rules matching a device identifier.")
        List<DeviceIdRule> byIds,
        @Schema(description = "Rules matching vendor, model and optionally firmware.")
        List<ModelRule> byModel,
        @Schema(description = "Heartbeat interval in seconds used when no rule matches.")
        int defSeconds) {

    static HartbitIntervalCalculation defaultRules() {
        return new HartbitIntervalCalculation(List.of(), List.of(), 1800);
    }

    static DeviceIdRule deviceIdRule(int seconds, Set<String> deviceId) {
        return new DeviceIdRule(seconds, new TreeSet<>(deviceId));
    }

    static ModelRule modelRule(int seconds, String vendor, Pattern model) {
        return new ModelRule(seconds, vendor, model, null);
    }

    static ModelRule firmwareRule(int seconds, String vendor, Pattern model, Pattern firmware) {
        return new ModelRule(seconds, vendor, model, firmware);
    }

    Duration calculateInterval(BootNotification boot) {
        return Duration.ofSeconds(Stream.of(byIds, byModel)
                .flatMap(Collection::stream)
                .filter(rule -> rule.matches(boot))
                .findFirst()
                .map(Rule::seconds)
                .orElse(defSeconds));
    }

    interface Rule {
        boolean matches(BootNotification boot);

        int seconds();
    }

    @Schema(description = "Interval rule matching a set of device identifiers.")
    record DeviceIdRule(
            @Schema(description = "Heartbeat interval in seconds applied by this rule.")
            int seconds,
            @Schema(description = "Device identifiers this rule matches.")
            SortedSet<String> devices) implements Rule {

        @Override
        public boolean matches(BootNotification boot) {
            return devices.contains(boot.deviceId());
        }
    }

    @Schema(description = "Interval rule matching vendor, model and optionally firmware of a device.")
    record ModelRule(
            @Schema(description = "Heartbeat interval in seconds applied by this rule.")
            int seconds,
            @Schema(description = "Vendor matched exactly against the boot notification vendor.")
            String vendor,
            @Schema(description = "Regular expression matched against the boot notification model.")
            Pattern model,
            @Schema(description = "Regular expression matched against the boot notification firmware; "
                                  + "absent when any firmware matches.")
            Pattern firmware) implements Rule {

        @Override
        public boolean matches(BootNotification boot) {
            return Objects.equals(vendor, boot.vendor())
                   && model.matcher(boot.model()).matches()
                   && (firmware == null || firmware.matcher(boot.firmware()).matches());
        }
    }
}
