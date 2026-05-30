package org.example.mcp.tools;

import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Tools for the MQTT sensor data stream — physically separate from the device
 * registry (sensors push numeric values into {@code sensor_readings} via MQTT
 * topics like {@code {prefix}/{deviceId}/{metric}}).
 *
 * <p>Use these when the user asks anything air-quality / climate / environmental
 * (CO2, humidity, temperature, pressure, gas, VOC, particulate matter, …) —
 * those are MQTT metrics, not device fields.</p>
 */
@Component
public class SensorTools {

    public record ReadingView(String device, String metric, double value, Instant recordedAt) {}

    private final SensorReadingRepository sensorReadingRepository;

    public SensorTools(SensorReadingRepository sensorReadingRepository) {
        this.sensorReadingRepository = sensorReadingRepository;
    }

    @Tool(name = "getLatestSensorReadings",
          description = "Latest reading for every MQTT sensor topic (one row per device+metric). " +
                        "Use this for any air-quality / climate / environmental question — CO2, humidity, " +
                        "temperature, pressure, gas, VOC, etc. live here, not in device state. " +
                        "Optional filters narrow by device name (the MQTT publisher id, e.g. 'living-room') " +
                        "and/or metric (e.g. 'co2', 'humidity', 'temperature').")
    public List<ReadingView> getLatestSensorReadings(
            @ToolParam(required = false, description = "Filter by device (matches SensorReading.room, the MQTT publisher id). Null = all devices.")
            String device,
            @ToolParam(required = false, description = "Filter by metric name (e.g. 'co2', 'humidity'). Null = all metrics.")
            String metric) {
        return sensorReadingRepository.findLatestPerTopic().stream()
                .filter(r -> device == null || device.equalsIgnoreCase(r.getRoom()))
                .filter(r -> metric == null || metric.equalsIgnoreCase(r.getMetric()))
                .map(r -> new ReadingView(r.getRoom(), r.getMetric(), r.getValue(), r.getRecordedAt()))
                .toList();
    }

    @Tool(name = "getSensorHistory",
          description = "Historical readings for a single device+metric combination, oldest first. " +
                        "Use to answer trend questions ('was CO2 high last night?'). " +
                        "The 'since' parameter is an ISO-8601 timestamp (e.g. 2026-05-24T20:00:00Z).")
    public List<ReadingView> getSensorHistory(
            @ToolParam(description = "Device name (MQTT publisher id, e.g. 'living-room').")
            String device,
            @ToolParam(description = "Metric name (e.g. 'co2', 'humidity', 'temperature').")
            String metric,
            @ToolParam(description = "ISO-8601 instant — readings strictly after this time are returned.")
            String since) {
        Instant fromInstant;
        try {
            fromInstant = Instant.parse(since);
        } catch (Exception e) {
            return List.of();
        }
        return sensorReadingRepository
                .findByRoomAndMetricAndRecordedAtAfterOrderByRecordedAtAsc(device, metric, fromInstant)
                .stream()
                .map(r -> new ReadingView(r.getRoom(), r.getMetric(), r.getValue(), r.getRecordedAt()))
                .toList();
    }
}
