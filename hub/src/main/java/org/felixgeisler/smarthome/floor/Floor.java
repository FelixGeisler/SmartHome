package org.felixgeisler.smarthome.floor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A named storey that groups rooms, like a Home Assistant "floor". */
@Entity
@Table(name = "floors")
public class Floor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false)
  private int level;

  /** Required by JPA. */
  protected Floor() {
    // Intentionally empty.
  }

  /**
   * Creates a floor.
   *
   * @param name the floor's unique name
   * @param level its sort order among floors
   */
  public Floor(String name, int level) {
    this.name = name;
    this.level = level;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getLevel() {
    return level;
  }

  /**
   * Renames the floor.
   *
   * @param name the new name
   */
  public void rename(String name) {
    this.name = name;
  }
}
