package org.example.integration;

import java.util.Map;
import java.util.Set;

/**
 * Capability interface for adapter factories that support an automated
 * one-click setup flow (e.g. Hue bridge button pairing).
 *
 * Implementing this interface is all a new adapter factory needs — the
 * controller discovers it dynamically and needs no modification.
 */
public interface AutoSetupCapable {

    /**
     * Attempt automatic setup.
     *
     * @param knownIp if non-blank, skip discovery and connect directly to this IP
     * @param skip    IPs already used by other instances of this adapter — skip them during discovery
     * @return result map, must include {@code "success": boolean} and {@code "message": string}.
     *         May include {@code "linkButtonRequired": true} to prompt the user.
     */
    Map<String, Object> autoSetup(String knownIp, Set<String> skip);

    /**
     * The config-map key that stores the bridge/device IP address.
     * Used by the controller to collect already-configured IPs before discovery.
     */
    String autoSetupIpConfigKey();
}
