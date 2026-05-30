package org.example.integration;

import java.util.Map;
import java.util.Set;

/**
 * Capability interface for adapter factories that can scan the local network
 * for devices of their type.
 *
 * Implementing this interface is all a new adapter factory needs in order to
 * expose a network-scan endpoint — the controller discovers it dynamically
 * via {@code instanceof} and needs no modification.
 */
public interface NetworkScannable {

    /**
     * Scan the local network.
     *
     * @param configuredIps IPs already in use by existing instances of this adapter —
     *                      they should be flagged {@code alreadyConfigured: true} in the result.
     * @return {@code {"found": [...], "error"?: "..."}}
     */
    Map<String, Object> scanNetwork(Set<String> configuredIps);

    /**
     * The config-map key that stores the primary device IP address.
     * Used by the controller to collect already-configured IPs before scanning.
     */
    String scanIpConfigKey();
}
