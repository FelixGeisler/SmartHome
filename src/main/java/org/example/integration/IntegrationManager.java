package org.example.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IntegrationManager {

    private static final Logger log = LoggerFactory.getLogger(IntegrationManager.class);

    private final Map<String, AdapterFactory> factories;
    private final IntegrationInstanceRepository instanceRepo;
    private final ObjectMapper objectMapper;

    /** instanceId → running adapter */
    private final ConcurrentHashMap<Long, DeviceAdapter> live = new ConcurrentHashMap<>();

    public IntegrationManager(List<AdapterFactory> factories,
                               IntegrationInstanceRepository instanceRepo,
                               ObjectMapper objectMapper) {
        this.factories = new LinkedHashMap<>();
        factories.forEach(f -> this.factories.put(f.getType(), f));
        this.instanceRepo = instanceRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        instanceRepo.findByEnabled(true).forEach(this::startInstance);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void startInstance(IntegrationInstance instance) {
        AdapterFactory factory = factories.get(instance.getAdapterType());
        if (factory == null) {
            log.warn("No factory registered for adapter type '{}' (instance {})", instance.getAdapterType(), instance.getId());
            return;
        }
        try {
            Map<String, String> config = parseConfig(instance.getConfigJson());
            DeviceAdapter adapter = factory.create(instance.getId(), instance.getName(), config);
            live.put(instance.getId(), adapter);
            adapter.start();
            log.info("Started {} adapter '{}' (id={})", instance.getAdapterType(), instance.getName(), instance.getId());
        } catch (Exception e) {
            log.error("Failed to start adapter for instance {} '{}': {}", instance.getId(), instance.getName(), e.getMessage());
        }
    }

    public void stopInstance(Long instanceId) {
        DeviceAdapter adapter = live.remove(instanceId);
        if (adapter != null) {
            try { adapter.stop(); } catch (Exception e) { log.warn("Error stopping adapter {}: {}", instanceId, e.getMessage()); }
            log.info("Stopped adapter for instance {}", instanceId);
        }
    }

    public void restartInstance(IntegrationInstance instance) {
        stopInstance(instance.getId());
        if (instance.isEnabled()) startInstance(instance);
    }

    // ── Routing ────────────────────────────────────────────────────────────────

    /**
     * Find the live adapter responsible for a device.
     * Looks up the device's integration instance directly — every device must
     * have an {@code integrationInstanceId} assigned when it is registered.
     * If the id is null the device was created before instance tracking was added
     * and cannot be routed; a warning is logged.
     */
    public Optional<DeviceAdapter> findForDevice(Device device) {
        if (device.getIntegrationInstanceId() == null) {
            log.warn("[IntegrationManager] Device '{}' (id={}, type={}) has no integrationInstanceId — " +
                     "re-discover it via the Integrations settings page.",
                     device.getName(), device.getId(), device.getType());
            return Optional.empty();
        }
        return Optional.ofNullable(live.get(device.getIntegrationInstanceId()));
    }

    // ── Discovery ──────────────────────────────────────────────────────────────

    public Map<String, Integer> discoverAll() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (DeviceAdapter adapter : live.values()) {
            try {
                List<Device> found = adapter.discoverDevices();
                result.merge(adapter.getAdapterType(), found.size(), Integer::sum);
            } catch (Exception e) {
                log.error("Discovery failed for instance {}: {}", adapter.getInstanceId(), e.getMessage());
            }
        }
        return result;
    }

    public List<Device> discoverInstance(Long instanceId) {
        DeviceAdapter adapter = live.get(instanceId);
        if (adapter == null) return List.of();
        return adapter.discoverDevices();
    }

    // ── Test ───────────────────────────────────────────────────────────────────

    public Map<String, Object> testConnection(Long instanceId) {
        DeviceAdapter adapter = live.get(instanceId);
        if (adapter == null) return Map.of("success", false, "message", "Adapter is not running — check config and re-save.");
        try {
            return adapter.testConnection();
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    // ── Metadata ───────────────────────────────────────────────────────────────

    public Collection<AdapterFactory> getFactories() {
        return factories.values();
    }

    public boolean isRunning(Long instanceId) {
        return live.containsKey(instanceId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, String> parseConfig(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Could not parse instance configJson: {}", e.getMessage());
            return Map.of();
        }
    }
}
