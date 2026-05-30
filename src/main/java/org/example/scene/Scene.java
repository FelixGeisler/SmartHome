package org.example.scene;

import jakarta.persistence.*;

@Entity
@Table(name = "scenes")
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Emoji icon, e.g. "🌙" */
    private String icon;

    /**
     * JSON array of action objects:
     * [{"deviceId": 1, "payloadJson": "{\"on\":true}"}, ...]
     */
    @Column(name = "actions_json", columnDefinition = "TEXT", nullable = false)
    private String actionsJson = "[]";

    public Long   getId()                         { return id; }
    public void   setId(Long id)                  { this.id = id; }

    public String getName()                       { return name; }
    public void   setName(String name)            { this.name = name; }

    public String getIcon()                       { return icon; }
    public void   setIcon(String icon)            { this.icon = icon; }

    public String getActionsJson()                { return actionsJson; }
    public void   setActionsJson(String actionsJson) { this.actionsJson = actionsJson; }
}
