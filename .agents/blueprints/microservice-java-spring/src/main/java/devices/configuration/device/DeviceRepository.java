package devices.configuration.device;

import java.util.Optional;

interface DeviceRepository {

    Optional<DeviceConfigurationEditor> get(String deviceId);

    void save(DeviceConfigurationEditor device);
}
