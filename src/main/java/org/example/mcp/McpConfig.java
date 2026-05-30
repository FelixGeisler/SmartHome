package org.example.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.example.mcp.tools.AutomationTools;
import org.example.mcp.tools.DeviceTools;
import org.example.mcp.tools.HomeSnapshotTools;
import org.example.mcp.tools.RoomTools;
import org.example.mcp.tools.SceneTools;
import org.example.mcp.tools.SensorTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registers SmartHome MCP tools and resources.
 *
 * <p>Spring AI's MCP server starter picks up every {@link ToolCallbackProvider}
 * bean (tools) and every {@code List<SyncResourceSpecification>} bean (resources)
 * and exposes them over the configured transport — see
 * {@code spring.ai.mcp.server.*} in application.properties.</p>
 */
@Configuration
public class McpConfig {

    private static final String JSON = "application/json";

    private static final String SNAPSHOT_URI    = "home://snapshot";
    private static final String FLOOR_PLAN_URI  = "home://floor-plan";

    @Bean
    public ToolCallbackProvider smartHomeTools(DeviceTools deviceTools,
                                               RoomTools roomTools,
                                               SceneTools sceneTools,
                                               AutomationTools automationTools,
                                               HomeSnapshotTools homeSnapshotTools,
                                               SensorTools sensorTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(deviceTools, roomTools, sceneTools, automationTools, homeSnapshotTools, sensorTools)
                .build();
    }

    @Bean
    public List<SyncResourceSpecification> smartHomeResources(HomeSnapshotTools homeSnapshotTools,
                                                              FloorPlanAssembler floorPlanAssembler,
                                                              ObjectMapper objectMapper) {

        var snapshotResource = new Resource(
                SNAPSHOT_URI,
                "Home snapshot",
                "Live whole-home state: every room with its devices and current state, plus counts and recent automation events.",
                JSON, null);

        var floorPlanResource = new Resource(
                FLOOR_PLAN_URI,
                "Floor plan",
                "Spatial layout — floors, rooms (rectangles), and devices positioned within each room.",
                JSON, null);

        return List.of(
                new SyncResourceSpecification(snapshotResource, (exchange, request) ->
                        json(SNAPSHOT_URI, homeSnapshotTools.getHomeSnapshot(), objectMapper)),
                new SyncResourceSpecification(floorPlanResource, (exchange, request) ->
                        json(FLOOR_PLAN_URI, floorPlanAssembler.assemble(), objectMapper))
        );
    }

    private static ReadResourceResult json(String uri, Object payload, ObjectMapper mapper) {
        try {
            return new ReadResourceResult(List.of(
                    new TextResourceContents(uri, JSON, mapper.writeValueAsString(payload))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise MCP resource " + uri, e);
        }
    }
}
