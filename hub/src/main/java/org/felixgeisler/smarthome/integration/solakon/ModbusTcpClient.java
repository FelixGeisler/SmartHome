package org.felixgeisler.smarthome.integration.solakon;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A minimal, read-only Modbus TCP client reading holding registers (function code 0x03).
 *
 * <p>Not thread-safe: a client belongs to the single poll that opened it and is closed with it.
 */
final class ModbusTcpClient implements AutoCloseable {

  private static final int READ_HOLDING_REGISTERS = 0x03;
  private static final int ERROR_FLAG = 0x80;

  /** MBAP header length: transaction id (2) + protocol id (2) + length (2) + unit id (1). */
  private static final int MBAP_HEADER_LENGTH = 7;

  /** The Modbus protocol caps a single register read at 125 registers. */
  private static final int MAX_REGISTERS = 125;

  private final int unitId;
  private final Socket socket;
  private final DataInputStream in;
  private final OutputStream out;
  private int transactionId;

  ModbusTcpClient(String host, int port, int unitId, int timeoutMillis) throws IOException {
    this.unitId = unitId;
    this.socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), timeoutMillis);
    socket.setSoTimeout(timeoutMillis);
    this.in = new DataInputStream(socket.getInputStream());
    this.out = socket.getOutputStream();
  }

  /**
   * Reads {@code count} consecutive holding registers starting at {@code address}.
   *
   * @param address the address of the first register
   * @param count how many registers to read (1..125)
   * @return the register words, each an unsigned 16-bit value (0..65535)
   * @throws IOException if the request fails, times out, or the device answers with an error
   */
  int[] readHoldingRegisters(int address, int count) throws IOException {
    if (count < 1 || count > MAX_REGISTERS) {
      throw new IllegalArgumentException("register count out of range: " + count);
    }
    writeRequest(address, count);
    return readResponse(count);
  }

  private void writeRequest(int address, int count) throws IOException {
    transactionId = (transactionId + 1) & 0xFFFF;
    byte[] frame = new byte[12];
    frame[0] = (byte) (transactionId >>> 8);
    frame[1] = (byte) transactionId;
    // Bytes 2..4 stay zero: protocol id 0x0000 and the high byte of the length.
    frame[5] = 6; // length: unit id (1) + function (1) + address (2) + count (2)
    frame[6] = (byte) unitId;
    frame[7] = (byte) READ_HOLDING_REGISTERS;
    frame[8] = (byte) (address >>> 8);
    frame[9] = (byte) address;
    frame[10] = (byte) (count >>> 8);
    frame[11] = (byte) count;
    out.write(frame);
    out.flush();
  }

  private int[] readResponse(int count) throws IOException {
    in.readFully(new byte[MBAP_HEADER_LENGTH]);
    int functionCode = in.readUnsignedByte();
    if (functionCode == (READ_HOLDING_REGISTERS | ERROR_FLAG)) {
      throw new IOException("Modbus exception, code " + in.readUnsignedByte());
    }
    if (functionCode != READ_HOLDING_REGISTERS) {
      throw new IOException("Unexpected Modbus function code " + functionCode);
    }
    int byteCount = in.readUnsignedByte();
    if (byteCount != count * 2) {
      throw new IOException("Unexpected Modbus byte count " + byteCount);
    }
    byte[] data = new byte[byteCount];
    in.readFully(data);
    int[] registers = new int[count];
    for (int i = 0; i < count; i++) {
      registers[i] = ((data[i * 2] & 0xFF) << 8) | (data[i * 2 + 1] & 0xFF);
    }
    return registers;
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
