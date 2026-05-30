package org.example.device;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final SensorReadingRepository sensorReadingRepository;

    public DeviceService(DeviceRepository deviceRepository,
                         SensorReadingRepository sensorReadingRepository) {
        this.deviceRepository = deviceRepository;
        this.sensorReadingRepository = sensorReadingRepository;
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    public Optional<Device> findById(Long id) {
        return deviceRepository.findById(id);
    }

    public Optional<Device> findByExternalId(String externalId) {
        return deviceRepository.findByExternalId(externalId);
    }

    @Transactional
    public Device registerDevice(String externalId, String name, DeviceType type, String room, Long integrationInstanceId) {
        return deviceRepository.findByExternalId(externalId).orElseGet(() -> {
            Device d = new Device();
            d.setExternalId(externalId);
            d.setName(name);
            d.setType(type);
            d.setRoom(room);
            d.setOnline(true);
            d.setLastSeen(Instant.now());
            d.setIntegrationInstanceId(integrationInstanceId);
            return deviceRepository.save(d);
        });
    }

    @Transactional
    public Device registerDevice(String externalId, String name, DeviceType type, String room) {
        return registerDevice(externalId, name, type, room, null);
    }

    @Transactional
    public void updateState(String externalId, String stateJson) {
        deviceRepository.findByExternalId(externalId).ifPresent(d -> {
            d.setLastStateJson(stateJson);
            d.setLastSeen(Instant.now());
            d.setOnline(true);
            deviceRepository.save(d);
        });
    }

    @Transactional
    public void markOffline(String externalId) {
        deviceRepository.findByExternalId(externalId).ifPresent(d -> {
            d.setOnline(false);
            deviceRepository.save(d);
        });
    }

    @Transactional
    public Optional<Device> updateDevice(Long id, String name, String room) {
        return updateDevice(id, name, room, null, null);
    }

    @Transactional
    public Optional<Device> updateDevice(Long id, String name, String room,
                                         Double roomX, Double roomY) {
        return deviceRepository.findById(id).map(d -> {
            if (name  != null) d.setName(name);
            if (room  != null) d.setRoom(room.isBlank() ? null : room);
            if (roomX != null) d.setRoomX(roomX);
            if (roomY != null) d.setRoomY(roomY);
            return deviceRepository.save(d);
        });
    }

    @Transactional
    public Optional<Device> updatePosition(Long id, Double x, Double y) {
        return deviceRepository.findById(id).map(d -> {
            d.setRoomX(x);
            d.setRoomY(y);
            return deviceRepository.save(d);
        });
    }

    @Transactional
    public void deleteById(Long id) {
        deviceRepository.deleteById(id);
    }

    @Transactional
    public void deleteDevicesByType(DeviceType type) {
        deviceRepository.deleteAll(deviceRepository.findByType(type));
    }

    @Transactional
    public void deleteDevicesByInstanceId(Long integrationInstanceId) {
        deviceRepository.deleteAll(deviceRepository.findByIntegrationInstanceId(integrationInstanceId));
    }

    public Optional<SensorReading> getLatestReading(String room, String metric) {
        return sensorReadingRepository.findByRoomAndMetricOrderByRecordedAtDesc(room, metric)
                .stream().findFirst();
    }

    public List<SensorReading> getLatestReadingsPerTopic() {
        return sensorReadingRepository.findLatestPerTopic();
    }
}
