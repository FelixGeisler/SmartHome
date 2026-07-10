package org.felixgeisler.smarthome.dashboard;

import java.util.List;

/**
 * A saved dashboard arrangement of placed cards.
 *
 * @param cards the placed cards, never null
 */
public record DashboardLayout(List<CardLayout> cards) {

  /** Null-guards and defensively copies the card list so the layout is immutable. */
  public DashboardLayout {
    cards = cards == null ? List.of() : List.copyOf(cards);
  }

  /**
   * One dashboard card's placement and its hidden sensor charts.
   *
   * @param deviceId the id of the device shown in the card
   * @param x the column of the card's left edge, in grid units
   * @param y the row of the card's top edge, in grid units
   * @param w the card's width in grid units
   * @param h the card's height in grid units
   * @param hiddenSensors the sensor keys whose charts are hidden, never null
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
