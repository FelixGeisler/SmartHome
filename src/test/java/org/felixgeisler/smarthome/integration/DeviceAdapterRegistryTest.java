package org.felixgeisler.smarthome.integration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceAdapterRegistryTest {

  private final DeviceAdapter shelly = new StubAdapter("shelly");

  @Test
  void get_returnsAdapterMatchingType() {
    DeviceAdapterRegistry registry = new DeviceAdapterRegistry(List.of(shelly));

    assertSame(shelly, registry.get("shelly"));
  }

  @Test
  void get_throwsForUnknownType() {
    DeviceAdapterRegistry registry = new DeviceAdapterRegistry(List.of(shelly));

    assertThrows(UnknownAdapterException.class, () -> registry.get("mqtt"));
  }

  private static final class StubAdapter implements DeviceAdapter {

    private final String type;

    StubAdapter(String type) {
      this.type = type;
    }

    @Override
    public String adapterType() {
      return type;
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
      // No-op: this stub only needs an identity for routing.
    }

    @Override
    public Map<String, Object> getState(String externalId) {
      return Map.of();
    }
  }
}
