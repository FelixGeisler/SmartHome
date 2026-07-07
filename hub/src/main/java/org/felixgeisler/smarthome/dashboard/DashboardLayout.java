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
   * One dashboard card's placement: the device it shows, its position and size in grid units, and
   * which of the device's sensor charts the user hid on this card.
   *
   * @param deviceId the id of the device shown in the card
   * @param x the column of the card's left edge, in grid units
   * @param y the row of the card's top edge, in grid units
   * @param w the card's width in grid units
   * @param h the card's height in grid units
   * @param hiddenSensors the keys of the device's sensors whose charts are hidden on this card,
   *     never null; empty means every chart is shown
   */
  public record CardLayout(
      long deviceId, int x, int y, int w, int h, List<String> hiddenSensors) {

    /** Null-guards and defensively copies the hidden-sensor list so the card is immutable. */
    public CardLayout {
      hiddenSensors = hiddenSensors == null ? List.of() : List.copyOf(hiddenSensors);
    }

    /**
     * Creates a card with every one of its sensor charts shown.
     *
     * @param deviceId the id of the device shown in the card
     * @param x the column of the card's left edge, in grid units
     * @param y the row of the card's top edge, in grid units
     * @param w the card's width in grid units
     * @param h the card's height in grid units
     */
    public CardLayout(long deviceId, int x, int y, int w, int h) {
      this(deviceId, x, y, w, h, List.of());
    }
  }
}
