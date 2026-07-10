package org.felixgeisler.smarthome.integration.solakon;

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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
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

class SolakonConnectionTest {

  private FakeInverter inverter;
  private DeviceService devices;
  private SettingsStore settings;
  private SolakonConnection connection;

  @BeforeEach
  void setUp() throws IOException {
    inverter = new FakeInverter();
    devices = mock(DeviceService.class);
    settings = mock(SettingsStore.class);
    when(settings.get(anyString())).thenReturn(Optional.empty());
    connection = new SolakonConnection(new SolakonProperties(1), devices, settings);
  }

  @AfterEach
  void tearDown() throws IOException {
    connection.stop();
    inverter.close();
  }

  @DisplayName("connect() registers the inverter as a sensing device declaring every metric")
  @Test
  void connect_registersInverterWithMetricSensors() {
    boolean connected = connection.connect("localhost", inverter.port(), 1);

    assertTrue(connected);
    // ArgumentCaptor.forClass cannot express List<SensorSpec>; the unchecked cast is safe here.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SensorSpec>> sensors = ArgumentCaptor.forClass(List.class);
    verify(devices)
        .register(
            eq("solakon-one"),
            eq("Solakon ONE"),
            eq(DeviceType.SOLAR_INVERTER),
            isNull(),
            isNull(),
            sensors.capture());
    List<SensorType> declared = sensors.getValue().stream().map(SensorSpec::type).toList();
    assertEquals(SolakonMetric.values().length, declared.size());
    assertTrue(declared.contains(SensorType.PV_POWER));
    assertTrue(declared.contains(SensorType.BATTERY_SOC));
  }

  @DisplayName("connect() persists the endpoint so it can be restored after a restart")
  @Test
  void connect_persistsEndpoint() {
    int port = inverter.port();

    connection.connect("localhost", port, 2);

    verify(settings).save("solakon.host", "localhost");
    verify(settings).save("solakon.port", Integer.toString(port));
    verify(settings).save("solakon.unitId", "2");
    assertTrue(connection.isConnected());
  }

  @DisplayName("connect() polls the inverter and records a reading for each metric")
  @Test
  void connect_pollsAndRecordsEachMetric() {
    connection.connect("localhost", inverter.port(), 1);

    for (SolakonMetric metric : SolakonMetric.values()) {
      verify(devices, timeout(3000))
          .recordReading(eq("solakon-one"), eq(metric.getSensorType().getKey()), anyString());
    }
  }

  @DisplayName("connect() reports failure without registering when the inverter is unreachable")
  @Test
  void connect_reportsFailureWhenUnreachable() throws IOException {
    int deadPort;
    try (ServerSocket free = new ServerSocket(0)) {
      deadPort = free.getLocalPort();
    }

    boolean connected = connection.connect("localhost", deadPort, 1);

    assertFalse(connected);
    assertFalse(connection.isConnected());
    verify(devices, never()).register(any(), any(), any(), any(), any(), any());
    verify(settings, never()).save(eq("solakon.host"), anyString());
  }

  @DisplayName("a failed connect attempt leaves an existing connection polling")
  @Test
  void connect_failedAttemptKeepsExistingConnectionPolling() throws IOException {
    connection.connect("localhost", inverter.port(), 1);
    verify(devices, timeout(3000).atLeastOnce())
        .recordReading(eq("solakon-one"), anyString(), anyString());
    int deadPort;
    try (ServerSocket free = new ServerSocket(0)) {
      deadPort = free.getLocalPort();
    }

    boolean reconnected = connection.connect("localhost", deadPort, 1);

    assertFalse(reconnected);
    assertTrue(connection.isConnected());
    // The original inverter must still be polled after the failed reconnect.
    clearInvocations(devices);
    verify(devices, timeout(3000).atLeastOnce())
        .recordReading(eq("solakon-one"), anyString(), anyString());
  }

  @DisplayName("disconnect() stops polling and forgets the saved endpoint")
  @Test
  void disconnect_clearsSavedEndpoint() {
    connection.connect("localhost", inverter.port(), 1);

    connection.disconnect();

    assertFalse(connection.isConnected());
    verify(settings).remove("solakon.host");
    verify(settings).remove("solakon.port");
    verify(settings).remove("solakon.unitId");
  }

  @DisplayName("reconnectLast() restores a connection from saved settings on startup")
  @Test
  void reconnectLast_restoresFromSavedSettings() {
    when(settings.get("solakon.host")).thenReturn(Optional.of("localhost"));
    when(settings.get("solakon.port")).thenReturn(Optional.of(Integer.toString(inverter.port())));
    when(settings.get("solakon.unitId")).thenReturn(Optional.of("1"));

    connection.reconnectLast();

    assertTrue(connection.isConnected());
    verify(devices).register(eq("solakon-one"), any(), any(), any(), any(), any());
  }

  /** A stand-in inverter that answers any function 0x03 read with zero-valued registers. */
  private static final class FakeInverter implements AutoCloseable {

    private final ServerSocket serverSocket;

    FakeInverter() throws IOException {
      this.serverSocket = new ServerSocket(0);
      Thread accept = new Thread(this::acceptLoop, "fake-inverter");
      accept.setDaemon(true);
      accept.start();
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
      while (!serverSocket.isClosed()) {
        try {
          Socket socket = serverSocket.accept();
          Thread handler = new Thread(() -> serve(socket), "fake-inverter-conn");
          handler.setDaemon(true);
          handler.start();
        } catch (IOException closed) {
          return; // server socket closed; stop accepting
        }
      }
    }

    private void serve(Socket socket) {
      try (socket) {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        OutputStream out = socket.getOutputStream();
        byte[] request = new byte[12];
        while (readRequest(in, request)) {
          int count = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);
          out.write(response(count));
          out.flush();
        }
      } catch (IOException disconnected) {
        return; // client closed the connection; this handler is done
      }
    }

    private static boolean readRequest(DataInputStream in, byte[] buffer) throws IOException {
      try {
        in.readFully(buffer);
        return true;
      } catch (EOFException closed) {
        return false; // client closed the connection cleanly
      }
    }

    private static byte[] response(int count) {
      int byteCount = count * 2;
      byte[] frame = new byte[9 + byteCount];
      frame[5] = (byte) (3 + byteCount); // length: unit + function + byte count + data
      frame[6] = 1; // unit id
      frame[7] = 0x03; // function code
      frame[8] = (byte) byteCount;
      // Register data stays zero; these tests assert on lifecycle, not values.
      return frame;
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }
}
