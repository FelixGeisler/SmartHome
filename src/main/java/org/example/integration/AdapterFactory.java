package org.example.integration;

import java.util.List;
import java.util.Map;

/**
 * Spring @Component that describes one integration type and creates adapter instances.
 * Add a new integration by implementing this interface and annotating with @Component.
 */
public interface AdapterFactory {

    /** Unique type ID, e.g. "hue", "shelly", "mqtt". Matches IntegrationInstance.adapterType. */
    String getType();

    /** Human-readable name shown in the UI, e.g. "Philips Hue Bridge". */
    String getDisplayName();

    /** Config fields needed for this adapter type — rendered as a form in the UI. */
    List<ConfigField> getConfigSchema();

    /** Create a live adapter instance from the given config. The returned adapter has not been started yet. */
    DeviceAdapter create(Long instanceId, String instanceName, Map<String, String> config);
}
