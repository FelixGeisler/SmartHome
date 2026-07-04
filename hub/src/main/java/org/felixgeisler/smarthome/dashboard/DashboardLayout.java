package org.felixgeisler.smarthome.dashboard;

import java.util.List;

/**
 * A saved dashboard arrangement: each card's device and its position and size on the grid. This is
 * a UI concern stored verbatim; the hub does not interpret the grid coordinates, which the frontend
 * (a drag-and-drop grid) owns.
 *
 * @param cards the placed cards, never null
 */
public record DashboardLayout(List<CardLayout> cards) {

  /** Null-guards and defensively copies the card list so the layout is immutable. */
  public DashboardLayout {
    cards = cards == null ? List.of() : List.copyOf(cards);
  }

  /**
   * The empty layout, used when nothing has been saved yet.
   *
   * @return a layout with no cards
   */
  public static DashboardLayout empty() {
    return new DashboardLayout(List.of());
  }

  /**
   * One dashboard card's placement: the device it shows and its position and size in grid units.
   *
   * @param deviceId the id of the device shown in the card
   * @param x the column of the card's left edge, in grid units
   * @param y the row of the card's top edge, in grid units
   * @param w the card's width in grid units
   * @param h the card's height in grid units
   */
  public record CardLayout(long deviceId, int x, int y, int w, int h) {}
}
