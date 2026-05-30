package org.example.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.integration.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationInstanceController {

    record TypeDto(String type, String displayName, List<ConfigField> schema) {}

    record InstanceDto(
            Long id, String adapterType, String adapterDisplayName,
            String name, Map<String, String> config,
            boolean enabled, boolean running
    ) {}

    private final IntegrationInstanceRepository instanceRepo;
    private final IntegrationManager manager;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    public IntegrationInstanceController(IntegrationInstanceRepository instanceRepo,
                                          IntegrationManager manager,
                                          DeviceService deviceService,
                                          ObjectMapper objectMapper) {
        this.instanceRepo = instanceRepo;
        this.manager      = manager;
        this.deviceService = deviceService;
        this.objectMapper = objectMapper;
    }

    // ── Adapter types ─────────────────────────────────────────────────────────

    @GetMapping("/types")
    public List<TypeDto> listTypes() {
        return manager.getFactories().stream()
                .map(f -> new TypeDto(f.getType(), f.getDisplayName(), f.getConfigSchema()))
                .collect(Collectors.toList());
    }

    // ── Instances CRUD ────────────────────────────────────────────────────────

    @GetMapping("/instances")
    public List<InstanceDto> listInstances() {
        return instanceRepo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/instances/{id}")
    public ResponseEntity<InstanceDto> getInstance(@PathVariable Long id) {
        return instanceRepo.findById(id)
                .map(i -> ResponseEntity.ok(toDto(i)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/instances")
    @Transactional
    public ResponseEntity<InstanceDto> createInstance(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("adapterType");
        String name = (String) body.get("name");
        if (type == null || name == null || type.isBlank() || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        @SuppressWarnings("unchecked")
        Map<String, String> config = body.get("config") instanceof Map<?,?> m
                ? (Map<String, String>) m : Map.of();

        IntegrationInstance instance = new IntegrationInstance();
        instance.setAdapterType(type);
        instance.setName(name);
        instance.setConfigJson(toJson(config));
        instance.setEnabled(true);
        instanceRepo.save(instance);

        manager.startInstance(instance);
        return ResponseEntity.ok(toDto(instance));
    }

    @PutMapping("/instances/{id}")
    @Transactional
    public ResponseEntity<InstanceDto> updateInstance(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return instanceRepo.findById(id).map(instance -> {
            if (body.get("name") instanceof String s && !s.isBlank()) instance.setName(s);
            if (body.get("enabled") instanceof Boolean b) instance.setEnabled(b);
            @SuppressWarnings("unchecked")
            Map<String, String> config = body.get("config") instanceof Map<?,?> m
                    ? (Map<String, String>) m : null;
            if (config != null) instance.setConfigJson(toJson(config));
            instanceRepo.save(instance);
            manager.restartInstance(instance);
            return ResponseEntity.ok(toDto(instance));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/instances/{id}")
    @Transactional
    public ResponseEntity<Void> deleteInstance(@PathVariable Long id) {
        if (instanceRepo.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        manager.stopInstance(id);
        deviceService.deleteDevicesByInstanceId(id);
        instanceRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @PostMapping("/instances/{id}/test")
    public Map<String, Object> testConnection(@PathVariable Long id) {
        return manager.testConnection(id);
    }

    @PostMapping("/instances/{id}/discover")
    public Map<String, Object> discover(@PathVariable Long id) {
        List<Device> found = manager.discoverInstance(id);
        return Map.of("discovered", found.size(),
                      "devices", found.stream().map(d -> Map.of(
                              "id", d.getId(),
                              "name", d.getName(),
                              "externalId", d.getExternalId()
                      )).collect(Collectors.toList()));
    }

    // ── Capability-based endpoints ────────────────────────────────────────────
    // These endpoints are completely generic: adding a new adapter factory that
    // implements one of the capability interfaces (NetworkScannable, AutoSetupCapable,
    // CcuDiscoverable) is sufficient — no controller changes required.

    /**
     * Scan the local network for devices of the given adapter type.
     * The factory must implement {@link NetworkScannable}.
     */
    @PostMapping("/scan/{adapterType}")
    public ResponseEntity<Map<String, Object>> scanNetwork(@PathVariable String adapterType) {
        AdapterFactory factory = findFactory(adapterType);
        if (!(factory instanceof NetworkScannable scannable)) {
            return ResponseEntity.notFound().build();
        }
        Set<String> usedIps = configuredIps(adapterType, scannable.scanIpConfigKey());
        return ResponseEntity.ok(scannable.scanNetwork(usedIps));
    }

    /**
     * Run the automated setup flow for the given adapter type.
     * The factory must implement {@link AutoSetupCapable}.
     * Optional body: {@code {"ip": "..."}} to skip discovery and retry link-button.
     */
    @PostMapping("/auto-setup/{adapterType}")
    public ResponseEntity<Map<String, Object>> autoSetup(
            @PathVariable String adapterType,
            @RequestBody(required = false) Map<String, String> body) {
        AdapterFactory factory = findFactory(adapterType);
        if (!(factory instanceof AutoSetupCapable capable)) {
            return ResponseEntity.notFound().build();
        }
        String knownIp = body != null ? body.getOrDefault("ip", "") : "";
        // When knownIp is set we are retrying after link-button — no IP skipping needed
        Set<String> skip = knownIp.isBlank()
                ? configuredIps(adapterType, capable.autoSetupIpConfigKey())
                : Set.of();
        return ResponseEntity.ok(capable.autoSetup(knownIp, skip));
    }

    /**
     * Discover the CCU/gateway for the given adapter type on the local network.
     * The factory must implement {@link CcuDiscoverable}.
     */
    @PostMapping("/discover-ccu/{adapterType}")
    public ResponseEntity<Map<String, Object>> discoverCcu(@PathVariable String adapterType) {
        AdapterFactory factory = findFactory(adapterType);
        if (!(factory instanceof CcuDiscoverable discoverable)) {
            return ResponseEntity.notFound().build();
        }
        Set<String> usedIps = configuredIps(adapterType, discoverable.discoveryIpConfigKey());
        return ResponseEntity.ok(discoverable.discoverCcu(usedIps));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Find a factory by adapter type, or return {@code null} if not registered. */
    private AdapterFactory findFactory(String adapterType) {
        return manager.getFactories().stream()
                .filter(f -> f.getType().equals(adapterType))
                .findFirst()
                .orElse(null);
    }

    /** Collect the value of {@code configKey} from every instance of {@code adapterType}. */
    private Set<String> configuredIps(String adapterType, String configKey) {
        return instanceRepo.findAll().stream()
                .filter(i -> adapterType.equals(i.getAdapterType()))
                .map(i -> manager.parseConfig(i.getConfigJson()).getOrDefault(configKey, ""))
                .filter(ip -> !ip.isBlank())
                .collect(Collectors.toSet());
    }

    private InstanceDto toDto(IntegrationInstance i) {
        AdapterFactory factory = manager.getFactories().stream()
                .filter(f -> f.getType().equals(i.getAdapterType()))
                .findFirst().orElse(null);
        String displayName = factory != null ? factory.getDisplayName() : i.getAdapterType();
        Map<String, String> config = manager.parseConfig(i.getConfigJson());
        // Mask password fields
        if (factory != null) {
            factory.getConfigSchema().stream()
                    .filter(f -> "password".equals(f.type()) && config.containsKey(f.key()))
                    .forEach(f -> config.put(f.key(), ""));
        }
        return new InstanceDto(i.getId(), i.getAdapterType(), displayName,
                i.getName(), config, i.isEnabled(), manager.isRunning(i.getId()));
    }

    private String toJson(Map<String, String> config) {
        try { return objectMapper.writeValueAsString(config); }
        catch (Exception e) { return "{}"; }
    }
}
