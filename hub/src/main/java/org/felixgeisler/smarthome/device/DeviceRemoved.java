package org.felixgeisler.smarthome.device;

/**
 * Domain event published when a device is deleted.
 *
 * @param id the id of the removed device
 */
public record DeviceRemoved(long id) {}
