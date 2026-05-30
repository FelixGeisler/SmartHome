package org.example.room;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Emoji icon displayed in the UI, e.g. "🛋️" */
    private String icon;

    @Column(name = "sort_order")
    private int sortOrder;

    /** Which floor this room belongs to. Null = unassigned. */
    @Column(name = "floor_id")
    private Long floorId;

    // ── Floor-plan layout — primary rectangle (0–100 % of the canvas) ──────────
    @Column(name = "plan_x") private Double planX;
    @Column(name = "plan_y") private Double planY;
    @Column(name = "plan_w") private Double planW;
    @Column(name = "plan_h") private Double planH;

    // ── Optional second rectangle segment — enables L-shaped rooms ──────────
    // An L-shape is two axis-aligned rectangles that share the same room identity.
    // Null = no second segment (plain rectangle room).
    @Column(name = "plan_x2") private Double planX2;
    @Column(name = "plan_y2") private Double planY2;
    @Column(name = "plan_w2") private Double planW2;
    @Column(name = "plan_h2") private Double planH2;

    public Long getId()           { return id; }
    public void setId(Long id)    { this.id = id; }

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }

    public String getIcon()              { return icon; }
    public void   setIcon(String icon)   { this.icon = icon; }

    public int  getSortOrder()               { return sortOrder; }
    public void setSortOrder(int sortOrder)  { this.sortOrder = sortOrder; }

    public Long getFloorId()              { return floorId; }
    public void setFloorId(Long floorId)  { this.floorId = floorId; }

    public Double getPlanX() { return planX; }
    public void   setPlanX(Double planX) { this.planX = planX; }

    public Double getPlanY() { return planY; }
    public void   setPlanY(Double planY) { this.planY = planY; }

    public Double getPlanW() { return planW; }
    public void   setPlanW(Double planW) { this.planW = planW; }

    public Double getPlanH() { return planH; }
    public void   setPlanH(Double planH) { this.planH = planH; }

    public Double getPlanX2() { return planX2; }
    public void   setPlanX2(Double planX2) { this.planX2 = planX2; }

    public Double getPlanY2() { return planY2; }
    public void   setPlanY2(Double planY2) { this.planY2 = planY2; }

    public Double getPlanW2() { return planW2; }
    public void   setPlanW2(Double planW2) { this.planW2 = planW2; }

    public Double getPlanH2() { return planH2; }
    public void   setPlanH2(Double planH2) { this.planH2 = planH2; }
}
