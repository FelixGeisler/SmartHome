package org.example.scene;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.integration.DeviceAdapter;
import org.example.integration.IntegrationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates scene-level operations so REST ({@code SceneController}) and
 * MCP ({@code SceneTools}) call the same code path. Activation logic
 * previously lived inline in the controller.
 */
@Service
public class SceneService {

    private static final Logger log = LoggerFactory.getLogger(SceneService.class);

    /** Outcome of {@link #activate(Long)} — useful for both HTTP status mapping and LLM responses. */
    public record ActivationResult(Long sceneId, String sceneName,
                                   int actionsAttempted, int actionsSucceeded) {}

    private final SceneRepository sceneRepository;
    private final DeviceService deviceService;
    private final IntegrationManager integrationManager;
    private final ObjectMapper objectMapper;

    public SceneService(SceneRepository sceneRepository,
                        DeviceService deviceService,
                        IntegrationManager integrationManager,
                        ObjectMapper objectMapper) {
        this.sceneRepository    = sceneRepository;
        this.deviceService      = deviceService;
        this.integrationManager = integrationManager;
        this.objectMapper       = objectMapper;
    }

    /**
     * Parse the persisted {@code actionsJson} of a scene into typed {@link SceneAction}s.
     * Throws on malformed JSON — callers map that to an error response.
     */
    public List<SceneAction> parseActions(Scene scene) throws Exception {
        String json = scene.getActionsJson();
        if (json == null || json.isBlank()) return List.of();
        return objectMapper.readValue(json, new TypeReference<List<SceneAction>>() {});
    }

    /**
     * Run every action in the scene. Per-action failures are logged but never
     * abort the rest of the scene. Returns {@code Optional.empty()} when no
     * scene with that id exists; throws if the scene's actions JSON is malformed.
     */
    public Optional<ActivationResult> activate(Long sceneId) throws Exception {
        Optional<Scene> sceneOpt = sceneRepository.findById(sceneId);
        if (sceneOpt.isEmpty()) return Optional.empty();
        Scene scene = sceneOpt.get();
        List<SceneAction> actions = parseActions(scene);
        int succeeded = 0;
        for (SceneAction action : actions) {
            if (executeAction(scene, action)) succeeded++;
        }
        return Optional.of(new ActivationResult(
                scene.getId(), scene.getName(), actions.size(), succeeded));
    }

    private boolean executeAction(Scene scene, SceneAction action) {
        Optional<Device> deviceOpt = deviceService.findById(action.deviceId());
        if (deviceOpt.isEmpty()) {
            log.warn("Scene {} – device id {} not found", scene.getName(), action.deviceId());
            return false;
        }
        Device device = deviceOpt.get();
        Optional<DeviceAdapter> adapterOpt = integrationManager.findForDevice(device);
        if (adapterOpt.isEmpty()) {
            log.warn("Scene {} – no adapter for device '{}'", scene.getName(), device.getName());
            return false;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    action.payloadJson(), new TypeReference<Map<String, Object>>() {});
            adapterOpt.get().sendCommand(device.getExternalId(), payload);
            return true;
        } catch (Exception e) {
            log.warn("Scene {} – action on '{}' failed: {}",
                    scene.getName(), device.getName(), e.getMessage());
            return false;
        }
    }
}
