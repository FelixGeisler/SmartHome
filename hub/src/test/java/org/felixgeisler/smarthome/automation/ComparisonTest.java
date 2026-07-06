package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComparisonTest {

  @DisplayName("GREATER_THAN is satisfied only strictly above the threshold")
  @Test
  void greaterThan_satisfiedOnlyStrictlyAbove() {
    assertTrue(Comparison.GREATER_THAN.test(26, 25));
    assertFalse(Comparison.GREATER_THAN.test(25, 25));
    assertFalse(Comparison.GREATER_THAN.test(24, 25));
  }

  @DisplayName("GREATER_THAN_OR_EQUAL is satisfied at and above the threshold")
  @Test
  void greaterThanOrEqual_satisfiedAtAndAbove() {
    assertTrue(Comparison.GREATER_THAN_OR_EQUAL.test(26, 25));
    assertTrue(Comparison.GREATER_THAN_OR_EQUAL.test(25, 25));
    assertFalse(Comparison.GREATER_THAN_OR_EQUAL.test(24, 25));
  }

  @DisplayName("LESS_THAN is satisfied only strictly below the threshold")
  @Test
  void lessThan_satisfiedOnlyStrictlyBelow() {
    assertTrue(Comparison.LESS_THAN.test(24, 25));
    assertFalse(Comparison.LESS_THAN.test(25, 25));
    assertFalse(Comparison.LESS_THAN.test(26, 25));
  }

  @DisplayName("LESS_THAN_OR_EQUAL is satisfied at and below the threshold")
  @Test
  void lessThanOrEqual_satisfiedAtAndBelow() {
    assertTrue(Comparison.LESS_THAN_OR_EQUAL.test(24, 25));
    assertTrue(Comparison.LESS_THAN_OR_EQUAL.test(25, 25));
    assertFalse(Comparison.LESS_THAN_OR_EQUAL.test(26, 25));
  }
}
