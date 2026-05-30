package org.example.integration;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "integration_instances")
public class IntegrationInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matches AdapterFactory.getType() — e.g. "hue", "shelly", "mqtt" */
    @Column(nullable = false)
    private String adapterType;

    /** User-given label, e.g. "Living Room Bridge" */
    @Column(nullable = false)
    private String name;

    /** Key-value config as JSON object, e.g. {"bridgeIp":"…","appKey":"…"} */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    private boolean enabled = true;

    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
