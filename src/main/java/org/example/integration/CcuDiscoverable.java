package org.example.integration;

import java.util.Map;
import java.util.Set;

/**
 * Capability interface for adapter factories that can discover a CCU / gateway
 * on the local network (e.g. Homematic CCU3 via SSDP).
 *
 * Implementing this interface is all a new adapter factory needs — the
 * controller discovers it dynamically and needs no modification.
 */
public interface CcuDiscoverable {

    /**
     * Scan the local network for a CCU.
     *
     * @param knownIps IPs already configured as other instances of this adapter —
     *                 skip them so a second unit can be found.
     * @return {@code {"found": true, "ip": "..."}} or {@code {"found": false, "message": "..."}}
     */
    Map<String, Object> discoverCcu(Set<String> knownIps);

    /**
     * The config-map key that stores the CCU IP address.
     * Used by the controller to collect already-configured IPs before discovery.
     */
    String discoveryIpConfigKey();
}
