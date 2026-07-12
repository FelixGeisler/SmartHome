package org.felixgeisler.smarthome.integration.solakonir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.felixgeisler.smarthome.device.DeviceService;
import org.felixgeisler.smarthome.device.DeviceType;
import org.felixgeisler.smarthome.device.SensorSpec;
import org.felixgeisler.smarthome.device.SensorType;
import org.felixgeisler.smarthome.settings.SettingsStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SolakonIrConnectionTest {

  private SolakonIrClient client;
  private DeviceService devices;
  private SettingsStore settings;
  private SolakonIrConnection connection;

  @BeforeEach
  void setUp() {
    client = mock(SolakonIrClient.class);
    devices = mock(DeviceService.class);
    settings = mock(SettingsStore.class);
    when(settings.get(anyString())).thenReturn(Optional.empty());
    when(client.read(anyString()))
        .thenReturn(
            new SolakonIrReading(
                new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("2")));
    connection = new SolakonIrConnection(new SolakonIrProperties(1), client, devices, settings);
  }

  @AfterEach
  void tearDown() {
    connection.stop();
  }

  @DisplayName("connect() registers the meter as a sensing grid device declaring every grid sensor")
  @Test
  void connect_registersMeterWithGridSensors() {
    boolean connected = connection.connect("192.168.1.60");

    assertTrue(connected);
    // ArgumentCaptor.forClass cannot express List<SensorSpec>; the unchecked cast is safe here.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SensorSpec>> sensors = ArgumentCaptor.forClass(List.class);
    verify(devices)
        .register(
            eq("solakon-ir-meter"),
            eq("Solakon IR Meter"),
            eq(DeviceType.GRID_METER),
            isNull(),
            isNull(),
            sensors.capture());
    List<SensorType> declared = sensors.getValue().stream().map(SensorSpec::type).toList();
    assertEquals(
        List.of(
            SensorType.GRID_IMPORT_POWER,
            SensorType.GRID_EXPORT_POWER,
            SensorType.GRID_IMPORT_ENERGY,
            SensorType.GRID_EXPORT_ENERGY),
        declared);
  }

  @DisplayName("connect() persists the host so it can be restored after a restart")
  @Test
  void connect_persistsHost() {
    connection.connect("192.168.1.60");

    verify(settings).save("solakonIr.host", "192.168.1.60");
    assertTrue(connection.isConnected());
  }

  @DisplayName("connect() polls the meter and records the split import and export readings")
  @Test
  void connect_pollsAndRecordsSplitReadings() {
    connection.connect("192.168.1.60");

    verify(devices, timeout(3000)).recordReading("solakon-ir-meter", "gridImportPower", "100");
    verify(devices, timeout(3000)).recordReading("solakon-ir-meter", "gridExportPower", "0");
    verify(devices, timeout(3000)).recordReading("solakon-ir-meter", "gridImportEnergy", "5");
    verify(devices, timeout(3000)).recordReading("solakon-ir-meter", "gridExportEnergy", "2");
  }

  @DisplayName("connect() reports failure without registering when the meter is unreachable")
  @Test
  void connect_reportsFailureWhenUnreachable() {
    when(client.read("192.168.1.99")).thenThrow(new SolakonIrException("unreachable"));

    boolean connected = connection.connect("192.168.1.99");

    assertFalse(connected);
    assertFalse(connection.isConnected());
    verify(devices, never()).register(any(), any(), any(), any(), any(), any());
    verify(settings, never()).save(eq("solakonIr.host"), anyString());
  }

  @DisplayName("a failed connect attempt leaves an existing connection polling")
  @Test
  void connect_failedAttemptKeepsExistingConnectionPolling() {
    connection.connect("192.168.1.60");
    verify(devices, timeout(3000).atLeastOnce())
        .recordReading(eq("solakon-ir-meter"), anyString(), anyString());
    when(client.read("192.168.1.99")).thenThrow(new SolakonIrException("unreachable"));

    boolean reconnected = connection.connect("192.168.1.99");

    assertFalse(reconnected);
    assertTrue(connection.isConnected());
    // The original meter must still be polled after the failed reconnect.
    clearInvocations(devices);
    verify(devices, timeout(3000).atLeastOnce())
        .recordReading(eq("solakon-ir-meter"), anyString(), anyString());
  }

  @DisplayName("disconnect() stops polling and forgets the saved host")
  @Test
  void disconnect_clearsSavedHost() {
    connection.connect("192.168.1.60");

    connection.disconnect();

    assertFalse(connection.isConnected());
    verify(settings).remove("solakonIr.host");
  }

  @DisplayName("reconnectLast() restores a connection from saved settings on startup")
  @Test
  void reconnectLast_restoresFromSavedSettings() {
    when(settings.get("solakonIr.host")).thenReturn(Optional.of("192.168.1.60"));

    connection.reconnectLast();

    assertTrue(connection.isConnected());
    verify(devices).register(eq("solakon-ir-meter"), any(), any(), any(), any(), any());
  }
}
