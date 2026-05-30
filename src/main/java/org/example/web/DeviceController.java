package org.example.web;

import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.integration.IntegrationManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final IntegrationManager integrationManager;
    private final WebSocketBroadcaster broadcaster;

    public DeviceController(DeviceService deviceService,
                            IntegrationManager integrationManager,
                            WebSocketBroadcaster broadcaster) {
        this.deviceService      = deviceService;
        this.integrationManager = integrationManager;
        this.broadcaster        = broadcaster;
    }

    @GetMapping
    public List<Device> listAll() {
        return deviceService.getAllDevices();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Device> getById(@PathVariable Long id) {
        return deviceService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<Map<String, Object>> getLiveState(@PathVariable Long id) {
        var deviceOpt = deviceService.findById(id);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();
        return integrationManager.findForDevice(device)
                .map(adapter -> ResponseEntity.ok(adapter.getState(device.getExternalId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/command")
    public ResponseEntity<Void> sendCommand(@PathVariable Long id,
                                            @RequestBody Map<String, Object> commandPayload) {
        var deviceOpt = deviceService.findById(id);
        if (deviceOpt.isEmpty()) return ResponseEntity.notFound().build();
        Device device = deviceOpt.get();
        return integrationManager.findForDevice(device).map(adapter -> {
            adapter.sendCommand(device.getExternalId(), commandPayload);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Device> updateDevice(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        String name  = body.get("name")  instanceof String s ? s : null;
        String room  = body.get("room")  instanceof String s ? s : null;
        Double roomX = body.get("roomX") instanceof Number n ? n.doubleValue() : null;
        Double roomY = body.get("roomY") instanceof Number n ? n.doubleValue() : null;
        return deviceService.updateDevice(id, name, room, roomX, roomY)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        if (deviceService.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        deviceService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/by-type/{type}")
    public ResponseEntity<Void> deleteByType(@PathVariable String type) {
        try {
            deviceService.deleteDevicesByType(
                    org.example.device.DeviceType.valueOf(type.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Sets or clears the floor-plan position of a device.
     * Send {@code {"x": 45.2, "y": 30.0}} to place it, or {@code {"x": null, "y": null}} to remove.
     */
    @PutMapping("/{id}/position")
    public ResponseEntity<Device> setPosition(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        Double x = body.get("x") instanceof Number n ? n.doubleValue() : null;
        Double y = body.get("y") instanceof Number n ? n.doubleValue() : null;
        return deviceService.updatePosition(id, x, y)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/discover")
    public Map<String, Integer> discoverAll() {
        Map<String, Integer> result = integrationManager.discoverAll();
        broadcaster.broadcastAllDevices(deviceService.getAllDevices());
        return result;
    }
}
