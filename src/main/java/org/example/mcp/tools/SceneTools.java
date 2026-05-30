package org.example.mcp.tools;

import org.example.mcp.ViewMapper;
import org.example.mcp.dto.SceneView;
import org.example.scene.SceneRepository;
import org.example.scene.SceneService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SceneTools {

    private final SceneRepository sceneRepository;
    private final SceneService sceneService;
    private final ViewMapper viewMapper;

    public SceneTools(SceneRepository sceneRepository,
                      SceneService sceneService,
                      ViewMapper viewMapper) {
        this.sceneRepository = sceneRepository;
        this.sceneService    = sceneService;
        this.viewMapper      = viewMapper;
    }

    @Tool(name = "listScenes",
          description = "List all configured scenes (preset multi-device commands), with name, icon and action count.")
    public List<SceneView> listScenes() {
        return sceneRepository.findAll().stream()
                .map(viewMapper::toSceneView)
                .toList();
    }

    @Tool(name = "activateScene",
          description = "Activate a scene — runs every configured device action in one go. " +
                        "Returns the scene name and how many actions succeeded vs were attempted. " +
                        "Individual action failures are logged but never abort the rest of the scene.")
    public SceneService.ActivationResult activateScene(
            @ToolParam(description = "Scene id from listScenes.")
            long sceneId) {
        try {
            return sceneService.activate(sceneId).orElse(null);
        } catch (Exception e) {
            return new SceneService.ActivationResult(sceneId, null, 0, 0);
        }
    }
}
