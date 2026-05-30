package org.example.room;

import jakarta.persistence.*;

@Entity
@Table(name = "floors")
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;       // "Erdgeschoss", "1. Obergeschoss", …

    @Column(name = "sort_order")
    private int sortOrder;

    public Long getId()              { return id; }
    public void setId(Long id)       { this.id = id; }

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }

    public int  getSortOrder()               { return sortOrder; }
    public void setSortOrder(int sortOrder)  { this.sortOrder = sortOrder; }
}
