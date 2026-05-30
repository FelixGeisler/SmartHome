package org.example.device;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByExternalId(String externalId);
    List<Device> findByType(DeviceType type);
    List<Device> findByRoom(String room);
    List<Device> findByIntegrationInstanceId(Long integrationInstanceId);
}
