package devices.configuration.device;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.*;
import java.util.Optional;

@Repository
@AllArgsConstructor
class DeviceDocumentSimpleRepository implements DeviceRepository {

    private final DocumentRepository documents;

    @Override
    public Optional<DeviceConfigurationEditor> get(String deviceId) {
        return documents.findById(deviceId)
                .map(DeviceDocumentEntity::getDevice);
    }

    @Override
    public void save(DeviceConfigurationEditor device) {
        documents.save(documents.findById(device.deviceId)
                .orElseGet(() -> new DeviceDocumentEntity(device.deviceId))
                .setDevice(device)
        );
    }

    @Repository
    interface DocumentRepository extends CrudRepository<DeviceDocumentEntity, String>, PagingAndSortingRepository<DeviceDocumentEntity, String> {
    }

    @Entity
    @Table(name = "device_document")
    @NoArgsConstructor
    static class DeviceDocumentEntity {
        @Id
        private String deviceId;
        @Version
        private long version;

        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private DeviceConfigurationEditor device;

        public DeviceDocumentEntity setDevice(DeviceConfigurationEditor device) {
            this.device = device;
            return this;
        }

        DeviceDocumentEntity(String deviceId) {
            this.deviceId = deviceId;
        }
    }

}
