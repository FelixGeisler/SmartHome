package org.felixgeisler.smarthome.integration.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MqttConnectionTest {

  @DisplayName("effectiveClientId keeps a configured id verbatim")
  @Test
  void effectiveClientId_keepsConfiguredId() {
    assertEquals("my-hub", MqttConnection.effectiveClientId("my-hub", "anyhost"));
  }

  @DisplayName("effectiveClientId derives a host-unique id when none is configured")
  @Test
  void effectiveClientId_derivesFromHost() {
    assertEquals("smarthome-hub-devbox", MqttConnection.effectiveClientId(null, "devbox"));
  }

  @DisplayName("effectiveClientId uses only the short host name, dropping the domain")
  @Test
  void effectiveClientId_dropsDomain() {
    assertEquals(
        "smarthome-hub-felix-pc", MqttConnection.effectiveClientId("  ", "felix-pc.fritz.box"));
  }

  @DisplayName("effectiveClientId falls back to the plain id when the host is unknown")
  @Test
  void effectiveClientId_fallsBackWhenHostUnknown() {
    assertEquals("smarthome-hub", MqttConnection.effectiveClientId(null, null));
  }
}
