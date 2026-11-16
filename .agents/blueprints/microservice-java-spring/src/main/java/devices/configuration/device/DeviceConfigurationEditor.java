package devices.configuration.device;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static devices.configuration.device.DomainEvent.*;

/**
 * A charge point operated by a fleet operator.
 *
 * <p>A device is identified by its {@code deviceId} and is in exactly one of two
 * states. It is <em>unowned</em> — not yet claimed by an operator, and therefore
 * not configurable at all — or it is <em>owned</em>, and the owning operator may
 * decide where it stands, when it serves customers and how it behaves.
 *
 * <p>Every accepted change is recorded as a {@link DomainEvent}, so the state of
 * an owned device is the sum of the business facts that happened to it, and
 * {@link #toDeviceConfiguration()} reads that state back as a snapshot. A device
 * that cannot operate yet — unowned, without a location, or meant to be public
 * without one — is not rejected; it is described by {@link Violations}, so the
 * operator can see what is still missing.
 *
 * <p>Instances are created through {@link #newDevice(String)}. The aggregate is
 * package-private: its lifecycle is driven by the device service and the
 * adapters of this bounded context, never from outside it.
 */
@AllArgsConstructor
class DeviceConfigurationEditor {
    /** Identity of the charge point; stable for the whole lifetime of the device. */
    final String deviceId;

    /** The business facts this device has recorded, in the order they were accepted. */
    final List<DomainEvent> events;
    private Ownership ownership;
    private Location location;
    private OpeningHours openingHours;
    private Settings settings;

    /**
     * Registers a device that no operator has claimed yet.
     *
     * <p>Such a device carries the out-of-the-box configuration — always open,
     * default settings, no location — which is also the state an operator sees
     * as "not configured".
     *
     * @param deviceId the identity the charge point will be known by from now on
     * @return an unowned device in its out-of-the-box configuration
     */
    static DeviceConfigurationEditor newDevice(String deviceId) {
        return new DeviceConfigurationEditor(
                deviceId,
                new ArrayList<>(),
                Ownership.unowned(),
                null,
                OpeningHours.alwaysOpened(),
                Settings.defaultSettings()
        );
    }

    /**
     * Returns the device to its out-of-the-box configuration while keeping its
     * owner — the configuration is reset, the ownership is not.
     */
    void resetToDefaults() {
        updateLocation(null);
        updateOpeningHours(OpeningHours.alwaysOpened());
        updateSettings(Settings.defaultSettings());
    }

    /**
     * Claims the device for an operator, or releases it back.
     *
     * @param ownership the operator and provider that from now on control this
     *                  device, or {@link Ownership#unowned()} to release it —
     *                  releasing discards the configuration too, because an
     *                  unowned device cannot be configured
     */
    void assignTo(Ownership ownership) {
        requireValue(ownership, "ownership");
        if (!Objects.equals(this.ownership, ownership)) {
            this.ownership = ownership;
            events.add(new OwnershipUpdated(deviceId, ownership));

            if (ownership.isUnowned()) {
                resetToDefaults();
            }
        }
    }

    /**
     * Records where the device is physically installed.
     *
     * <p>A device without a location cannot serve customers: it stays hidden
     * from the customer-facing map and is reported as incomplete until it has
     * one.
     *
     * @param location the installation address, or {@code null} to clear it
     */
    void updateLocation(Location location) {
        if (!Objects.equals(this.location, location)) {
            this.location = location;
            events.add(new LocationUpdated(deviceId, location));
        }
    }

    /**
     * Records when the device is available to customers.
     *
     * @param openingHours the availability window customers will be served in
     */
    void updateOpeningHours(OpeningHours openingHours) {
        requireValue(openingHours, "openingHours");
        if (!Objects.equals(this.openingHours, openingHours)) {
            this.openingHours = openingHours;
            events.add(new OpeningHoursUpdated(deviceId, openingHours));
        }
    }

    /**
     * Changes how the device behaves.
     *
     * <p>The change is merged into the current settings, so only the switches
     * named in {@code settings} move; the rest keep their value.
     *
     * @param settings the switches to merge into the device's current settings
     */
    void updateSettings(Settings settings) {
        requireValue(settings, "settings");
        Settings merged = this.settings.merge(settings);
        if (!Objects.equals(this.settings, merged)) {
            this.settings = merged;
            events.add(new SettingsUpdated(deviceId, this.settings));
        }
    }

    /**
     * Refuses a command that carries no value for a required section.
     *
     * @param value the value the command must carry
     * @param name  the section the value belongs to, named in the failure
     */
    private static void requireValue(Object value, String name) {
        if (value == null) {
            throw new DeviceException(DeviceException.Error.COMMAND_VALUE_MISSING, name);
        }
    }

    /**
     * Lists what this device is still missing before it can operate: an owner, a
     * provider, a location, or the combination of settings that makes it visible
     * to customers.
     */
    private Violations calculateViolations() {
        return Violations.builder()
                .operatorNotAssigned(ownership.operator() == null)
                .providerNotAssigned(ownership.provider() == null)
                .locationMissing(location == null)
                .showOnMapButMissingLocation(settings.isShowOnMap() && location == null)
                .showOnMapButNoPublicAccess(settings.isShowOnMap() && !settings.isPublicAccess())
                .build();
    }

    /**
     * Reads the device as a configuration snapshot: the current ownership,
     * location, opening hours and settings, together with the
     * {@link Violations} that keep it from operating and its
     * {@link Visibility} to customers.
     *
     * @return the device as the read side serves it
     */
    DeviceConfiguration toDeviceConfiguration() {
        Violations violations = calculateViolations();
        Visibility visibility = Visibility.calculateVisibility(
                violations.isValid() && settings.isPublicAccess(),
                settings.isShowOnMap()
        );
        return new DeviceConfiguration(
                deviceId,
                ownership, location,
                openingHours, settings,
                violations, visibility
        );
    }
}
