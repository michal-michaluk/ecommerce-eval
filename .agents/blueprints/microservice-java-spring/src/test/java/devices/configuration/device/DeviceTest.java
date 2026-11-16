package devices.configuration.device;

import tools.jackson.core.JacksonException;
import org.junit.jupiter.api.Test;

import static devices.configuration.device.DeviceConfigurationAssert.assertThat;
import static devices.configuration.device.DeviceFixture.*;
import static devices.configuration.device.Violations.builder;

class DeviceTest {

    @Test
    void newDeviceHasSettingsSetToDefaults() {
        DeviceConfigurationEditor device = DeviceConfigurationEditor.newDevice(DeviceFixture.randomId());

        assertThat(device)
                .hasSettings(Settings.defaultSettings())
                .hasViolationsLikeNotConfiguredDevice();
    }

    @Test
    void overrideSettings() {
        DeviceConfigurationEditor device = givenDevice();

        device.updateSettings(settingsForPublicDevice());

        assertThat(device)
                .hasSettings(settingsForPublicDevice())
                .hasNoViolations();
    }

    @Test
    void updateSingleValueInSettings() throws JacksonException {
        DeviceConfigurationEditor device = givenDevice();

        device.updateSettings(settingsWithAutoStartOnly());

        assertThat(device)
                .hasSettings(Settings.defaultSettings().toBuilder()
                        .autoStart(true))
                .hasNoViolations();
    }

    @Test
    void mergeSettings() throws JacksonException {
        DeviceConfigurationEditor device = givenDevice();

        device.updateSettings(settingsForPublicDevice());
        device.updateSettings(settingsWithAutoStartOnly());

        assertThat(device)
                .hasSettings(Settings.builder()
                        .publicAccess(true)
                        .showOnMap(true)
                        .autoStart(true)
                        .remoteControl(false)
                        .billing(false)
                        .reimbursement(false)
                )
                .hasNoViolations();
    }

    @Test
    void newDeviceHasOwnershipSetToUnowned() {
        DeviceConfigurationEditor device = DeviceConfigurationEditor.newDevice(DeviceFixture.randomId());

        assertThat(device)
                .hasOwnership(Ownership.unowned())
                .hasViolationsLikeNotConfiguredDevice();
    }

    @Test
    void assignStationToOwner() {
        DeviceConfigurationEditor device = givenDevice();
        device.assignTo(someOtherOwnership());

        assertThat(device)
                .hasOwnership(someOtherOwnership())
                .hasNoViolations();
    }

    @Test
    void resetOwnership() {
        DeviceConfigurationEditor device = givenDevice();

        device.assignTo(Ownership.unowned());

        assertThat(device)
                .hasOwnership(Ownership.unowned())
                .hasSettings(Settings.defaultSettings())
                .hasLocation(null)
                .hasOpeningHours(OpeningHours.alwaysOpened())
                .hasViolationsLikeNotConfiguredDevice();
    }

    @Test
    void resetToDefaults() {
        DeviceConfigurationEditor device = givenDevice();
        device.resetToDefaults();

        assertThat(device)
                .hasOwnership(DeviceFixture.ownership())
                .hasSettings(Settings.defaultSettings())
                .hasLocation(null)
                .hasOpeningHours(OpeningHours.alwaysOpened())
                .hasViolations(Violations.builder()
                        .locationMissing(true));
    }

    @Test
    void newDeviceHasNoLocation() {
        DeviceConfigurationEditor device = DeviceConfigurationEditor.newDevice(DeviceFixture.randomId());

        assertThat(device)
                .hasLocation(null)
                .hasViolationsLikeNotConfiguredDevice();
    }

    @Test
    void updateLocation() {
        DeviceConfigurationEditor device = DeviceConfigurationEditor.newDevice(DeviceFixture.randomId());
        device.updateLocation(location());

        assertThat(device)
                .hasLocation(location())
                .hasViolations(builder()
                        .providerNotAssigned(true)
                        .operatorNotAssigned(true)
                );
    }

    @Test
    void overrideLocation() {
        DeviceConfigurationEditor device = givenDevice();
        device.updateLocation(someOtherLocation());

        assertThat(device)
                .hasLocation(someOtherLocation())
                .hasNoViolations();
    }

    @Test
    void resetLocation() {
        DeviceConfigurationEditor device = givenDevice();
        device.updateLocation(null);

        assertThat(device)
                .hasLocation(null)
                .hasViolations(builder()
                        .locationMissing(true));
    }
}
