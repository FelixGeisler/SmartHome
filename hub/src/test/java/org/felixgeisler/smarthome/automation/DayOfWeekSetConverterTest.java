package org.felixgeisler.smarthome.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.DayOfWeek;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DayOfWeekSetConverterTest {

  private final DayOfWeekSetConverter converter = new DayOfWeekSetConverter();

  @DisplayName("round-trips a set of days as a sorted comma-separated list")
  @Test
  void roundTripsDays() {
    EnumSet<DayOfWeek> days = EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY);

    assertEquals("MONDAY,FRIDAY", converter.convertToDatabaseColumn(days));
    assertEquals(days, converter.convertToEntityAttribute("MONDAY,FRIDAY"));
  }

  @DisplayName("treats an empty set or a null/blank column as no days")
  @Test
  void emptyMeansNoDays() {
    assertNull(converter.convertToDatabaseColumn(EnumSet.noneOf(DayOfWeek.class)));
    assertNull(converter.convertToDatabaseColumn(null));
    assertEquals(EnumSet.noneOf(DayOfWeek.class), converter.convertToEntityAttribute(null));
    assertEquals(EnumSet.noneOf(DayOfWeek.class), converter.convertToEntityAttribute(" "));
  }

  @DisplayName("drops an unparseable day instead of failing the whole load")
  @Test
  void dropsUnparseableDay() {
    assertEquals(EnumSet.of(DayOfWeek.MONDAY), converter.convertToEntityAttribute("MONDAY,MONDY"));
  }
}
