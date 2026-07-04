package org.felixgeisler.smarthome.device;

/**
 * Domain event published when a device is registered or its state or latest readings change. It
 * lets outbound consumers (such as the live dashboard stream) react without the device service
 * depending on them.
 *
 * <p>The payload is deliberately the {@link DeviceResponse client view}, not the entity: the event
 * crosses threads, so it must be an immutable snapshot, and pushing exactly what the REST API
 * serves keeps live clients on a single device contract.
 *
 * @param device the device's current view at the moment it changed
 */
public record DeviceChanged(DeviceResponse device) {}
