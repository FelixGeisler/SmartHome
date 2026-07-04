package org.felixgeisler.smarthome.device;

/**
 * Domain event published when a device is deleted, letting outbound consumers (such as the live
 * dashboard stream) drop it without the device service depending on them.
 *
 * @param id the id of the removed device
 */
public record DeviceRemoved(long id) {}
