package org.example.device;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "sensor_readings", indexes = {
        @Index(name = "idx_topic_recorded", columnList = "topic, recorded_at")
})
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full MQTT topic, e.g. "smarthome/sensors/living-room/temperature" */
    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String room;

    /** Metric name: "temperature", "humidity", "airquality" */
    @Column(nullable = false)
    private String metric;

    /** Stored as "reading_value" to avoid H2's reserved keyword "value" */
    @Column(name = "reading_value", nullable = false)
    private double value;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
