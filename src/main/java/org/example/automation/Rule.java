package org.example.automation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "automation_rules")
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private boolean enabled = true;

    // ── Time-based trigger ────────────────────────────────────────
    /** "HH:mm" e.g. "07:00". Null or blank = rule has no time trigger (ignored). */
    @Column(name = "trigger_time")
    private String triggerTime;

    /**
     * Comma-separated ISO day-of-week values where 1=Monday … 7=Sunday.
     * E.g. "1,2,3,4,5" = weekdays. Null or blank = every day.
     */
    @Column(name = "trigger_days")
    private String triggerDays;

    // ── Action ───────────────────────────────────────────────────
    /** FK to Device.id */
    private Long targetDeviceId;

    /** Command payload as JSON, e.g. {"on": true} or {"setPointTemperature": 18.0} */
    @Column(columnDefinition = "TEXT")
    private String actionPayloadJson;

    /** Prevents re-firing within the same minute (set to 60 s by default). */
    private long cooldownMs = 60_000;

    private Instant lastTriggered;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTriggerTime() { return triggerTime; }
    public void setTriggerTime(String triggerTime) { this.triggerTime = triggerTime; }

    public String getTriggerDays() { return triggerDays; }
    public void setTriggerDays(String triggerDays) { this.triggerDays = triggerDays; }

    public Long getTargetDeviceId() { return targetDeviceId; }
    public void setTargetDeviceId(Long targetDeviceId) { this.targetDeviceId = targetDeviceId; }

    public String getActionPayloadJson() { return actionPayloadJson; }
    public void setActionPayloadJson(String actionPayloadJson) { this.actionPayloadJson = actionPayloadJson; }

    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long cooldownMs) { this.cooldownMs = cooldownMs; }

    public Instant getLastTriggered() { return lastTriggered; }
    public void setLastTriggered(Instant lastTriggered) { this.lastTriggered = lastTriggered; }
}
