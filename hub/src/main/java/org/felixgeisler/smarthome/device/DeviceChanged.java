package org.felixgeisler.smarthome.device;

/**
 * Domain event published when a device is registered or its state or readings change.
 *
 * <p>The payload is the {@link DeviceResponse client view}, not the entity: the event crosses
 * threads, so it must be an immutable snapshot.
 *
 * @param device the device's view at the moment it changed
 */
public record DeviceChanged(DeviceResponse device) {}
