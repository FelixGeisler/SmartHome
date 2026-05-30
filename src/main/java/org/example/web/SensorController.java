package org.example.web;

import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/sensors")
public class SensorController {

    private final SensorReadingRepository repository;

    public SensorController(SensorReadingRepository repository) {
        this.repository = repository;
    }

    /** Latest reading for every tracked topic — useful for dashboard overview */
    @GetMapping
    public List<SensorReading> latestAll() {
        return repository.findLatestPerTopic();
    }

    /** Latest N readings for a specific room/metric combination */
    @GetMapping("/{room}/{metric}")
    public List<SensorReading> latest(@PathVariable String room,
                                      @PathVariable String metric,
                                      @RequestParam(defaultValue = "10") int limit) {
        return repository.findByRoomAndMetricOrderByRecordedAtDesc(room, metric)
                .stream().limit(limit).toList();
    }

    /** Historical readings since a given ISO-8601 timestamp */
    @GetMapping("/{room}/{metric}/history")
    public List<SensorReading> history(@PathVariable String room,
                                       @PathVariable String metric,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return repository.findByRoomAndMetricAndRecordedAtAfterOrderByRecordedAtAsc(room, metric, since);
    }
}
