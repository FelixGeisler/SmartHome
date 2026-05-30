package org.example.integration;

import org.example.device.Device;
import java.util.List;
import java.util.Map;

/**
 * A live adapter instance managing one configured integration (e.g. one Hue bridge,
 * one Shelly device, one MQTT broker).  Instances are created by AdapterFactory and
 * managed by IntegrationManager.
 */
public interface DeviceAdapter {

    /** IntegrationInstance PK this adapter was created for. */
    Long getInstanceId();

    /** Matches AdapterFactory.getType(). */
    String getAdapterType();

    /** Called by IntegrationManager after creation — begin polling / SSE / MQTT subscription. */
    void start();

    /** Called by IntegrationManager when instance is removed or disabled — clean up threads / connections. */
    void stop();

    /** Verify connectivity without side-effects. Returns {"success": bool, "message": string}. */
    Map<String, Object> testConnection();

    /** Sync known devices to the DB and return the registered Device objects. */
    List<Device> discoverDevices();

    /** Fetch current live state for a single device (by its externalId). */
    Map<String, Object> getState(String externalId);

    /**
     * Send a command to a device.
     * Payload uses generic keys: "on" (boolean), "brightness" (0–100 double),
     * "colorTemp" (integer mirek), "colorX"/"colorY" (CIE xy doubles),
     * "setPointTemperature" (double °C).
     * Each adapter translates these to its own protocol format.
     */
    void sendCommand(String externalId, Map<String, Object> payload);
}
