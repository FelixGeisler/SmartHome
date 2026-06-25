package org.felixgeisler.smarthome.capability;

import java.util.Optional;

/**
 * The device-neutral vocabulary for state values and command arguments (ADR 3).
 *
 * <p>Each key owns its value type, unit, and valid range, plus how to parse, format, and validate
 * its values, so adding a neutral attribute is adding one self-describing constant. Adapters
 * translate between these neutral values and a device's native encoding; vendor scales, color
 * gamut, and the like never appear here.
 */
public enum AttributeKey {

  /** Whether a switchable device is on. */
  ON_OFF("on", null) {
    @Override
    public Object parse(String raw) {
      return Boolean.parseBoolean(raw);
    }

    @Override
    public Optional<String> validate(Object value) {
      return requireType(value, Boolean.class, "a boolean");
    }
  },

  /** Brightness percentage; 0 is not off (use {@link #ON_OFF}), so the range is 1..100. */
  BRIGHTNESS("brightness", "%") {
    @Override
    public Object parse(String raw) {
      return Integer.valueOf(raw);
    }

    @Override
    public Optional<String> validate(Object value) {
      return integerInRange(value, 1, 100);
    }
  },

  /** Color as CIE 1931 xy chromaticity. */
  COLOR_XY("colorXy", "CIE1931") {
    @Override
    public Object parse(String raw) {
      int comma = raw.indexOf(',');
      double x = Double.parseDouble(raw.substring(0, comma));
      double y = Double.parseDouble(raw.substring(comma + 1));
      return new XyColor(x, y);
    }

    @Override
    public String format(Object value) {
      XyColor xy = (XyColor) value;
      return xy.x() + "," + xy.y();
    }

    @Override
    public Optional<String> validate(Object value) {
      if (!(value instanceof XyColor(double x, double y))) {
        return Optional.of("must be a CIE xy color");
      }
      if (outsideUnitInterval(x) || outsideUnitInterval(y)) {
        return Optional.of("x and y must be in 0..1");
      }
      return Optional.empty();
    }
  },

  /** Color temperature in absolute Kelvin; a sanity-bound, real limit lives in the adapter. */
  COLOR_TEMPERATURE_K("colorTemperatureK", "K") {
    @Override
    public Object parse(String raw) {
      return Integer.valueOf(raw);
    }

    @Override
    public Optional<String> validate(Object value) {
      return integerInRange(value, 1000, 10000);
    }
  },

  /** The active color mode; reported (derived from the last color writing), never commanded. */
  COLOR_MODE("colorMode", null) {
    @Override
    public Object parse(String raw) {
      return ColorMode.valueOf(raw);
    }

    @Override
    public Optional<String> validate(Object value) {
      return requireType(value, ColorMode.class, "a color mode");
    }
  };

  private final String key;
  private final String unitSymbol;

  AttributeKey(String key, String unitSymbol) {
    this.key = key;
    this.unitSymbol = unitSymbol;
  }

  /** Returns the stable string key this attribute uses in the state map and adapter payloads. */
  public String wireKey() {
    return key;
  }

  /** Returns the unit of the value or empty when it has none. */
  public Optional<String> unit() {
    return Optional.ofNullable(unitSymbol);
  }

  /**
   * Parses a stored string into this attribute's value type.
   *
   * @param raw the stored representation
   * @return the typed value
   */
  public abstract Object parse(String raw);

  /**
   * Formats a typed value into its stored string representation.
   *
   * @param value the typed value
   * @return the stored representation
   */
  public String format(Object value) {
    return String.valueOf(value);
  }

  /**
   * Checks a value against this attribute's type and range.
   *
   * @param value the value to check
   * @return an error message when invalid, otherwise empty
   */
  public abstract Optional<String> validate(Object value);

  private static Optional<String> requireType(Object value, Class<?> type, String expected) {
    return type.isInstance(value) ? Optional.empty() : Optional.of("must be " + expected);
  }

  private static Optional<String> integerInRange(Object value, int min, int max) {
    if (!(value instanceof Integer n)) {
      return Optional.of("must be an integer");
    }
    if (n < min || n > max) {
      return Optional.of("must be in " + min + ".." + max);
    }
    return Optional.empty();
  }

  private static boolean outsideUnitInterval(double v) {
    return v < 0.0 || v > 1.0;
  }
}
