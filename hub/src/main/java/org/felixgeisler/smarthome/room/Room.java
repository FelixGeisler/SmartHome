package org.felixgeisler.smarthome.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.felixgeisler.smarthome.floor.Floor;

/**
 * A named grouping of devices, in the spirit of a Home Assistant "area": a device belongs to zero
 * or one room. A room carries no behavior of its own; it is organizational metadata.
 */
@Entity
@Table(name = "rooms")
public class Room {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String name;

  // The floor this room belongs to, or null when unassigned. Eagerly fetched because the response
  // view is built after the session closes.
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "floor_id")
  private Floor floor;

  /** Required by JPA. */
  protected Room() {
    // Intentionally empty.
  }

  /**
   * Creates a room.
   *
   * @param name the room's unique name
   */
  public Room(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  /**
   * Renames the room.
   *
   * @param name the new name
   */
  public void rename(String name) {
    this.name = name;
  }

  /**
   * Returns the floor this room belongs to, or null when it is unassigned.
   *
   * @return the floor, or null
   */
  public Floor getFloor() {
    return floor;
  }

  /**
   * Assigns this room to a floor.
   *
   * @param floor the floor to assign
   */
  public void assignFloor(Floor floor) {
    this.floor = floor;
  }

  /** Removes this room from its floor, leaving it unassigned. */
  @SuppressWarnings("PMD.NullAssignment")
  public void clearFloor() {
    // Null is the "unassigned" state for the optional floor association.
    this.floor = null;
  }
}
