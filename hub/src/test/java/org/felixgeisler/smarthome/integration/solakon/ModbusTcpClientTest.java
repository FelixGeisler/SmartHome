package org.felixgeisler.smarthome.integration.solakon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModbusTcpClientTest {

  private static final int TIMEOUT_MS = 2000;

  @DisplayName("readHoldingRegisters sends a valid 0x03 request and returns the response words")
  @Test
  void readHoldingRegisters_returnsResponseWords() throws Exception {
    byte[] response = readResponse(new int[] {0xFFFF, 0xFF9C});
    OneShotModbusServer server = OneShotModbusServer.start(response);
    try (server;
        ModbusTcpClient client = new ModbusTcpClient("localhost", server.port(), 7, TIMEOUT_MS)) {
      int[] words = client.readHoldingRegisters(39230, 2);

      assertArrayEquals(new int[] {0xFFFF, 0xFF9C}, words);
      byte[] request = server.awaitRequest();
      assertEquals(7, request[6] & 0xFF, "unit id");
      assertEquals(0x03, request[7] & 0xFF, "function code");
      assertEquals(39230, ((request[8] & 0xFF) << 8) | (request[9] & 0xFF), "start address");
      assertEquals(2, ((request[10] & 0xFF) << 8) | (request[11] & 0xFF), "register count");
    }
  }

  @DisplayName("readHoldingRegisters throws when the device returns a Modbus exception response")
  @Test
  void readHoldingRegisters_throwsOnExceptionResponse() throws Exception {
    OneShotModbusServer server = OneShotModbusServer.start(exceptionResponse(0x02));
    try (server;
        ModbusTcpClient client = new ModbusTcpClient("localhost", server.port(), 1, TIMEOUT_MS)) {
      assertThrows(IOException.class, () -> client.readHoldingRegisters(39230, 2));
    }
  }

  private static byte[] readResponse(int[] words) {
    int byteCount = words.length * 2;
    byte[] frame = new byte[9 + byteCount];
    frame[5] = (byte) (3 + byteCount); // length: unit + function + byte count + data
    frame[6] = 1; // unit id
    frame[7] = 0x03; // function code
    frame[8] = (byte) byteCount;
    for (int i = 0; i < words.length; i++) {
      frame[9 + i * 2] = (byte) (words[i] >>> 8);
      frame[9 + i * 2 + 1] = (byte) words[i];
    }
    return frame;
  }

  private static byte[] exceptionResponse(int exceptionCode) {
    byte[] frame = new byte[9];
    frame[5] = 3; // length: unit + function + exception code
    frame[6] = 1; // unit id
    frame[7] = (byte) 0x83; // 0x03 | error flag
    frame[8] = (byte) exceptionCode;
    return frame;
  }

  /** A throwaway Modbus TCP server that answers a single request with a canned response. */
  private static final class OneShotModbusServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final CountDownLatch handled = new CountDownLatch(1);
    private volatile byte[] request;
    private volatile IOException error;

    private OneShotModbusServer(ServerSocket serverSocket, byte[] response) {
      this.serverSocket = serverSocket;
      Thread thread = new Thread(() -> handle(response), "fake-modbus");
      thread.setDaemon(true);
      thread.start();
    }

    static OneShotModbusServer start(byte[] response) throws IOException {
      return new OneShotModbusServer(new ServerSocket(0), response);
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    private void handle(byte[] response) {
      try (Socket socket = serverSocket.accept()) {
        byte[] req = new byte[12];
        new DataInputStream(socket.getInputStream()).readFully(req);
        this.request = req;
        socket.getOutputStream().write(response);
        socket.getOutputStream().flush();
      } catch (IOException ex) {
        this.error = ex;
      } finally {
        handled.countDown();
      }
    }

    byte[] awaitRequest() throws InterruptedException {
      assertTrue(handled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "no request was handled");
      if (error != null) {
        throw new AssertionError("fake Modbus server failed", error);
      }
      return request;
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }
}
