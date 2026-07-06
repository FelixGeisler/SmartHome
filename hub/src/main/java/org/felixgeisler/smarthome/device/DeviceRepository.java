package org.felixgeisler.smarthome.device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Device} entities. */
public interface DeviceRepository extends JpaRepository<Device, Long> {

  /**
   * Finds a device by its integration-specific address.
   *
   * @param externalId the device's address within its integration
   * @return the matching device, if present
   */
  Optional<Device> findByExternalId(String externalId);

  /**
   * Finds all devices assigned to a room.
   *
   * @param roomId the room id
   * @return the devices in that room
   */
  List<Device> findByRoomId(Long roomId);
}
