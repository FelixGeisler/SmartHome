package org.example.device;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier from the external system (Hue UUID, Homematic SGTIN, MQTT topic segment) */
    @Column(nullable = false, unique = true)
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private DeviceType type;

    private String room;
    private boolean online;

    /** Last known full state as a raw JSON string */
    @Column(columnDefinition = "TEXT")
    private String lastStateJson;

    private Instant lastSeen;

    /** Which IntegrationInstance registered this device — null for devices registered before multi-instance support. */
    @Column
    private Long integrationInstanceId;

    /** Floor-plan X position as a percentage (0–100) within the assigned room. Null = not placed. */
    @Column(name = "room_x")
    private Double roomX;

    /** Floor-plan Y position as a percentage (0–100) within the assigned room. Null = not placed. */
    @Column(name = "room_y")
    private Double roomY;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public DeviceType getType() { return type; }
    public void setType(DeviceType type) { this.type = type; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getLastStateJson() { return lastStateJson; }
    public void setLastStateJson(String lastStateJson) { this.lastStateJson = lastStateJson; }

    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    public Long getIntegrationInstanceId() { return integrationInstanceId; }
    public void setIntegrationInstanceId(Long integrationInstanceId) { this.integrationInstanceId = integrationInstanceId; }

    public Double getRoomX() { return roomX; }
    public void   setRoomX(Double roomX) { this.roomX = roomX; }

    public Double getRoomY() { return roomY; }
    public void   setRoomY(Double roomY) { this.roomY = roomY; }
}
