package org.example.automation;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable record of a single automation rule firing.
 * Written by {@link RuleEngine} every time a rule executes successfully.
 * Never updated after creation — the log is append-only.
 */
@Entity
@Table(name = "automation_events",
       indexes = @Index(columnList = "fired_at DESC"))
public class AutomationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable rule name at the time of firing (denormalized for history stability). */
    @Column(nullable = false)
    private String ruleName;

    /** Target device id */
    private Long deviceId;

    /** Device name at the time of firing (denormalized). */
    private String deviceName;

    /** The exact JSON payload that was sent to the adapter. */
    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    public AutomationEvent() {}

    public AutomationEvent(String ruleName, Long deviceId, String deviceName,
                            String payloadJson, Instant firedAt) {
        this.ruleName   = ruleName;
        this.deviceId   = deviceId;
        this.deviceName = deviceName;
        this.payloadJson = payloadJson;
        this.firedAt    = firedAt;
    }

    public Long getId()           { return id; }
    public String getRuleName()   { return ruleName; }
    public Long getDeviceId()     { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getPayloadJson(){ return payloadJson; }
    public Instant getFiredAt()   { return firedAt; }
}
