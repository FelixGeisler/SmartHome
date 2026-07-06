package org.felixgeisler.smarthome.automation;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Persists a set of weekdays as a comma-separated list of day names in a single column. */
@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {

  @Override
  public String convertToDatabaseColumn(Set<DayOfWeek> days) {
    if (days == null || days.isEmpty()) {
      return null;
    }
    return days.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
  }

  @Override
  public Set<DayOfWeek> convertToEntityAttribute(String value) {
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    if (value == null || value.isBlank()) {
      return days;
    }
    for (String name : value.split(",")) {
      parseDay(name.trim()).ifPresent(days::add);
    }
    return days;
  }

  private static Optional<DayOfWeek> parseDay(String name) {
    if (name.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(DayOfWeek.valueOf(name));
    } catch (IllegalArgumentException ex) {
      // A stored day that no longer maps to a weekday (a corrupted or hand-edited row) is dropped
      // rather than throwing, which would fail loading every automation and the list endpoint.
      return Optional.empty();
    }
  }
}
