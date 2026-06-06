package org.felixgeisler.smarthome.device;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.felixgeisler.smarthome.integration.DeviceAdapterRegistry;
import org.springframework.stereotype.Service;

/** Registers devices and dispatches commands to the adapter that handles each one. */
@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final DeviceAdapterRegistry adapters;

  /**
   * Creates the service.
   *
   * @param devices the device repository
   * @param adapters the adapter registry used to reach devices
   */
  public DeviceService(DeviceRepository devices, DeviceAdapterRegistry adapters) {
    this.devices = devices;
    this.adapters = adapters;
  }

  /**
   * Returns all known devices.
   *
   * @return every registered device
   */
  public List<Device> getAllDevices() {
    return devices.findAll();
  }

  /**
   * Finds a device by id.
   *
   * @param id the device id
   * @return the device, if present
   */
  public Optional<Device> findById(Long id) {
    return devices.findById(id);
  }

  /**
   * Registers a device, or returns the existing one with the same external id.
   *
   * @param externalId the device's address within its integration
   * @param name human-readable device name
   * @param type the device category
   * @param adapterType identifier of the adapter that handles this device
   * @return the persisted device
   */
  public Device register(String externalId, String name, DeviceType type, String adapterType) {
    return devices
        .findByExternalId(externalId)
        .orElseGet(() -> devices.save(new Device(externalId, name, type, adapterType)));
  }

  /**
   * Flips a device on or off, commanding it through its adapter and persisting the new state.
   *
   * @param id the device id
   * @return the updated device
   * @throws DeviceNotFoundException if no device has the given id
   */
  public Device toggle(Long id) {
    Device device = devices.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
    boolean desired = !device.isOn();
    Map<String, Object> command = Map.of("on", desired);
    adapters.get(device.getAdapterType()).sendCommand(device.getExternalId(), command);
    device.setOn(desired);
    return devices.save(device);
  }
}
