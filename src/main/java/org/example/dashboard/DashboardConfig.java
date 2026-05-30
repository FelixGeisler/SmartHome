package org.example.dashboard;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dashboard_configs")
public class DashboardConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private int sortOrder = 0;

    /** react-grid-layout layouts JSON for all breakpoints (lg/md/sm) */
    @Column(columnDefinition = "TEXT")
    private String layoutJson = "{}";

    /** Widget list JSON: [{id, type, config}] */
    @Column(columnDefinition = "TEXT")
    private String widgetsJson = "[]";

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }

    public String getWidgetsJson() { return widgetsJson; }
    public void setWidgetsJson(String widgetsJson) { this.widgetsJson = widgetsJson; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
