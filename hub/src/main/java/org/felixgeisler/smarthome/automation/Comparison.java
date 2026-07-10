package org.felixgeisler.smarthome.automation;

/**
 * How a sensor reading is weighed against a threshold in a {@link TriggerKind#SENSOR_THRESHOLD}
 * trigger.
 */
public enum Comparison {

  /** Satisfied when the reading is strictly greater than the threshold. */
  GREATER_THAN {
    @Override
    public boolean test(double actual, double threshold) {
      return actual > threshold;
    }
  },

  /** Satisfied when the reading is greater than or equal to the threshold. */
  GREATER_THAN_OR_EQUAL {
    @Override
    public boolean test(double actual, double threshold) {
      return actual >= threshold;
    }
  },

  /** Satisfied when the reading is strictly less than the threshold. */
  LESS_THAN {
    @Override
    public boolean test(double actual, double threshold) {
      return actual < threshold;
    }
  },

  /** Satisfied when the reading is less than or equal to the threshold. */
  LESS_THAN_OR_EQUAL {
    @Override
    public boolean test(double actual, double threshold) {
      return actual <= threshold;
    }
  };

  /**
   * Tests whether an actual reading satisfies this comparison against the threshold.
   *
   * @param actual the sensor reading
   * @param threshold the configured threshold
   * @return true if the reading satisfies the comparison
   */
  public abstract boolean test(double actual, double threshold);
}
