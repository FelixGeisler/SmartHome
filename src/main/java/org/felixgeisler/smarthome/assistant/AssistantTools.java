package org.felixgeisler.smarthome.assistant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.felixgeisler.smarthome.assistant.AnthropicClient.Tool;
import org.felixgeisler.smarthome.capability.XyColor;
import org.felixgeisler.smarthome.device.CommandRequest;
import org.felixgeisler.smarthome.device.Device;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.Sensor;
import org.felixgeisler.smarthome.telemetry.ReadingPoint;
import org.felixgeisler.smarthome.telemetry.TelemetryHistoryService;
import org.springframework.stereotype.Component;

/**
 * The assistant's tools, mapping the model's calls onto the hub's existing services. Results are
 * returned as compact text the model reads back; a tool never throws — a failure becomes an error
 * string so the model can explain it.
 */
@Component
class AssistantTools {

  /** CO2 above this (ppm) is worth flagging to the user. */
  private static final int CO2_CONCERN_PPM = 1000;

  /** The tool argument naming a device by its external id. */
  private static final String DEVICE_ID = "deviceId";

  private final DeviceService devices;
  private final TelemetryHistoryService history;

  AssistantTools(DeviceService devices, TelemetryHistoryService history) {
    this.devices = devices;
    this.history = history;
  }

  /** The tool definitions advertised to the model. */
  List<Tool> definitions() {
    return List.of(
        new Tool(
            "list_devices",
            "List every device with its type, capabilities, current state, and each sensor's "
                + "latest reading. Call this first to learn the current state of the home.",
            schema(Map.of())),
        new Tool(
            "get_sensor_history",
            "Read a sensor's recent readings over a time window. Call this when the user asks "
                + "about trends, recent history, or whether a current reading is unusual.",
            schema(
                Map.of(
                    DEVICE_ID, stringProp("device external id, e.g. living-room"),
                    "sensorKey", stringProp("sensor key, e.g. temperature or co2"),
                    "hours", intProp("hours back to read; default 24")),
                List.of(DEVICE_ID, "sensorKey"))),
        new Tool(
            "control_device",
            "Turn a device on or off, set its brightness, or set the color or white color "
                + "temperature of a color light. Call this when the user asks to control a device. "
                + "Only command devices (lights, plugs) are controllable; sensor nodes are not.",
            schema(
                Map.of(
                    DEVICE_ID, stringProp("device external id"),
                    "on", boolProp("true to turn on, false to turn off"),
                    "brightness", intProp("brightness percentage, 1-100"),
                    "color",
                    stringProp(
                        "color as #RRGGBB hex, e.g. #0000FF for blue; only for color lights"),
                    "colorTemperatureK",
                    intProp("white color temperature in Kelvin, e.g. 2700 warm, 6500 cool")),
                List.of(DEVICE_ID))));
  }

  private static Map<String, Object> schema(Map<String, Object> props) {
    return Map.of("type", "object", "properties", props);
  }

  private static Map<String, Object> schema(Map<String, Object> props, List<String> required) {
    return Map.of("type", "object", "properties", props, "required", required);
  }

  private static Map<String, Object> stringProp(String description) {
    return prop("string", description);
  }

  private static Map<String, Object> intProp(String description) {
    return prop("integer", description);
  }

  private static Map<String, Object> boolProp(String description) {
    return prop("boolean", description);
  }

  private static Map<String, Object> prop(String type, String description) {
    return Map.of("type", type, "description", description);
  }

  /** Runs a tool by name and returns a text result for the model; never throws. */
  @SuppressWarnings("PMD.AvoidCatchingGenericException") // tool failures must not propagate
  String execute(String name, Map<String, Object> input) {
    try {
      Map<String, Object> args = input == null ? Map.of() : input;
      return switch (name) {
        case "list_devices" -> listDevices();
        case "get_sensor_history" -> sensorHistory(args);
        case "control_device" -> controlDevice(args);
        default -> "Error: unknown tool " + name;
      };
    } catch (RuntimeException ex) {
      // Any domain exception (not found, unsupported, invalid) becomes a result the model can read.
      return "Error: " + ex.getMessage();
    }
  }

  private String listDevices() {
    List<Device> all = devices.getAllDevices();
    if (all.isEmpty()) {
      return "No devices are registered.";
    }
    return all.stream().map(AssistantTools::describe).collect(Collectors.joining("\n"));
  }

  private static String describe(Device device) {
    StringBuilder line = new StringBuilder();
    line.append("- ")
        .append(device.getExternalId())
        .append(" \"")
        .append(device.getName())
        .append("\" (")
        .append(device.getType())
        .append("), capabilities ")
        .append(device.getCapabilities());
    if (!device.getState().isEmpty()) {
      line.append(", state ").append(device.getState());
    }
    List<Sensor> sensors = device.getSensors();
    if (!sensors.isEmpty()) {
      line.append(", readings: ")
          .append(
              sensors.stream()
                  .map(AssistantTools::reading)
                  .collect(Collectors.joining(", ")));
    }
    return line.toString();
  }

  private static String reading(Sensor sensor) {
    String value = sensor.getValue() == null ? "—" : sensor.getValue() + " " + sensor.getUnit();
    return sensor.getKey() + "=" + value;
  }

  private String sensorHistory(Map<String, Object> args) {
    String deviceId = required(args, DEVICE_ID);
    String sensorKey = required(args, "sensorKey");
    int hours = args.get("hours") instanceof Number n ? Math.max(1, n.intValue()) : 24;
    List<ReadingPoint> points = history.history(deviceId, sensorKey, Duration.ofHours(hours));
    if (points.isEmpty()) {
      return "No history for " + deviceId + "/" + sensorKey + " in the last " + hours + "h.";
    }
    double min = points.stream().mapToDouble(ReadingPoint::value).min().orElse(0);
    double max = points.stream().mapToDouble(ReadingPoint::value).max().orElse(0);
    ReadingPoint latest = points.get(points.size() - 1);
    return String.format(
        "%s/%s over %dh: %d readings; latest %.1f at %s; min %.1f, max %.1f.%s",
        deviceId,
        sensorKey,
        hours,
        points.size(),
        latest.value(),
        latest.timestamp(),
        min,
        max,
        "co2".equals(sensorKey) && max > CO2_CONCERN_PPM ? " (CO2 above " + CO2_CONCERN_PPM
            + " ppm — consider ventilating)" : "");
  }

  private String controlDevice(Map<String, Object> args) {
    String externalId = required(args, DEVICE_ID);
    Device device =
        devices.getAllDevices().stream()
            .filter(candidate -> candidate.getExternalId().equals(externalId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("no device '" + externalId + "'"));
    Boolean on = args.get("on") instanceof Boolean value ? value : null;
    Integer brightness = args.get("brightness") instanceof Number value ? value.intValue() : null;
    XyColor color = args.get("color") instanceof String hex ? XyColor.fromHex(hex) : null;
    Integer colorTemperatureK =
        args.get("colorTemperatureK") instanceof Number value ? value.intValue() : null;
    Device updated =
        devices.applyCommand(
            device.getId(), new CommandRequest(on, brightness, color, colorTemperatureK));
    return "OK: updated " + externalId + "; state now " + updated.getState();
  }

  private static String required(Map<String, Object> args, String key) {
    Object value = args.get(key);
    if (value == null) {
      throw new IllegalArgumentException("missing '" + key + "'");
    }
    return value.toString();
  }
}
