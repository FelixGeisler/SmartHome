package org.felixgeisler.smarthome.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceAdapterTest {

  @DisplayName("the default isReachable() reports reachable when the state read succeeds")
  @Test
  void isReachable_trueWhenStateReadSucceeds() {
    DeviceAdapter adapter = adapterReading(() -> Map.of("on", true));

    assertTrue(adapter.isReachable("ext-1"));
  }

  @DisplayName("the default isReachable() reports unreachable when the state read fails")
  @Test
  void isReachable_falseWhenStateReadFails() {
    DeviceAdapter adapter =
        adapterReading(
            () -> {
              throw new IllegalStateException("device offline");
            });

    assertFalse(adapter.isReachable("ext-1"));
  }

  private static DeviceAdapter adapterReading(Supplier<Map<String, Object>> read) {
    return new DeviceAdapter() {
      @Override
      public String adapterType() {
        return "test";
      }

      @Override
      public void sendCommand(String externalId, Map<String, Object> payload) {
        // Not exercised by these tests.
      }

      @Override
      public Map<String, Object> getState(String externalId) {
        return read.get();
      }
    };
  }
}
