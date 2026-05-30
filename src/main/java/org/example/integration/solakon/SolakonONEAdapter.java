package org.example.integration.solakon;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.device.Device;
import org.example.device.DeviceService;
import org.example.device.DeviceType;
import org.example.device.SensorReading;
import org.example.device.SensorReadingRepository;
import org.example.integration.DeviceAdapter;
import org.example.web.WebSocketBroadcaster;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Adapter for the Solakon ONE solar inverter via Modbus TCP.
 *
 * <p>Register addresses and scaling factors are taken from the official
 * Home Assistant integration: github.com/solakon-de/solakon-one-homeassistant
 *
 * <p>Reads are capped at {@value #MAX_REGS_PER_REQUEST} registers per TCP round-trip
 * to avoid silent rejections from devices with small PDU buffers.
 */
public class SolakonONEAdapter implements DeviceAdapter {

    private static final int FC_READ_HOLDING    = 0x03;
    private static final int MAX_REGS_PER_REQUEST = 30;   // conservative cap; spec allows 125

    /** Slave IDs tried by testConnection() in order. */
    private static final int[] PROBE_SLAVE_IDS = {1, 0, 100, 247, 255};

    private final Long   instanceId;
    private final String host;
    private final int    port;
    private final int    slaveId;

    private final DeviceService           deviceService;
    private final SensorReadingRepository sensorReadingRepository;
    private final WebSocketBroadcaster    broadcaster;
    private final ObjectMapper            objectMapper;

    private ScheduledExecutorService scheduler;

    SolakonONEAdapter(Long instanceId, String instanceName, Map<String, String> config,
                      DeviceService deviceService,
                      SensorReadingRepository sensorReadingRepository,
                      WebSocketBroadcaster broadcaster,
                      ObjectMapper objectMapper) {
        this.instanceId              = instanceId;
        this.host                    = config.getOrDefault("host", "").trim();
        this.port                    = parseInt(config.get("port"),    502);
        this.slaveId                 = parseInt(config.get("slaveId"), 1);
        this.deviceService           = deviceService;
        this.sensorReadingRepository = sensorReadingRepository;
        this.broadcaster             = broadcaster;
        this.objectMapper            = objectMapper;
    }

    @Override public Long   getInstanceId()  { return instanceId; }
    @Override public String getAdapterType() { return "solakon-one"; }

    // ── Modbus TCP client ─────────────────────────────────────────────────────

    /**
     * Read holding registers (FC 0x03) from the device using the configured slave ID.
     * Opens a fresh TCP connection for each call.
     *
     * @param address 0-indexed PDU register address (as used in the HA integration)
     * @param count   number of registers to read (capped internally by MAX_REGS_PER_REQUEST)
     * @return array of register values, or {@code null} on any error / timeout
     */
    private short[] readRegisters(int address, int count) {
        return readRegistersAs(address, count, slaveId);
    }

    /**
     * Same as {@link #readRegisters} but with an explicit slave ID — used by the
     * slave-ID probe logic in {@link #testConnection()}.
     */
    private short[] readRegistersAs(int address, int count, int unitId) {
        if (host.isBlank()) return null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(5_000);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream  in  = new DataInputStream(socket.getInputStream());

            // MBAP header (6 bytes) + PDU (6 bytes)
            out.writeShort(1);              // Transaction ID
            out.writeShort(0);              // Protocol ID = 0
            out.writeShort(6);              // Remaining length
            out.writeByte(unitId);          // Unit ID (Modbus slave address)
            out.writeByte(FC_READ_HOLDING); // Function code 0x03
            out.writeShort(address);        // Starting register address
            out.writeShort(count);          // Number of registers
            out.flush();

            // MBAP response header
            in.readShort();                 // Transaction ID echo
            in.readShort();                 // Protocol ID echo
            in.readUnsignedShort();         // Remaining length
            in.readUnsignedByte();          // Unit ID echo
            int fc = in.readUnsignedByte(); // Function code

            if ((fc & 0x80) != 0) {         // Error response: bit 7 set
                int errCode = in.readUnsignedByte();
                System.err.println("[SolakonONE:" + instanceId + "] Modbus exception "
                        + errCode + " @ addr " + address + " slave " + unitId);
                return null;
            }
            int byteCount = in.readUnsignedByte();
            short[] regs = new short[byteCount / 2];
            for (int i = 0; i < regs.length; i++) regs[i] = in.readShort();
            return regs;

        } catch (Exception e) {
            System.err.println("[SolakonONE:" + instanceId + "] readRegisters("
                    + address + "," + count + ") slave=" + unitId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Read an arbitrary-length register range, splitting into chunks of at most
     * {@value #MAX_REGS_PER_REQUEST} registers per TCP request.
     */
    private short[] readRange(int address, int totalCount) {
        short[] result = new short[totalCount];
        int read = 0;
        while (read < totalCount) {
            int chunk = Math.min(MAX_REGS_PER_REQUEST, totalCount - read);
            short[] part = readRegisters(address + read, chunk);
            if (part == null) return null;
            System.arraycopy(part, 0, result, read, part.length);
            read += part.length;
        }
        return result;
    }

    // ── Type conversion (matching HA modbus.py) ───────────────────────────────

    private static int  u16(short r)             { return r & 0xFFFF; }
    private static int  i16(short r)             { return r; }
    private static long u32(short hi, short lo)  { return ((long)(hi & 0xFFFF) << 16) | (lo & 0xFFFF); }
    private static int  i32(short hi, short lo)  { return ((hi & 0xFFFF) << 16) | (lo & 0xFFFF); }

    private static String regString(short[] regs) {
        byte[] bytes = new byte[regs.length * 2];
        for (int i = 0; i < regs.length; i++) {
            bytes[i * 2]     = (byte)((regs[i] >> 8) & 0xFF);
            bytes[i * 2 + 1] = (byte)( regs[i]       & 0xFF);
        }
        return new String(bytes, StandardCharsets.US_ASCII).replace("\0", "").trim();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "solakon-one-poll-" + instanceId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 15, 30, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ── DeviceAdapter ─────────────────────────────────────────────────────────

    /**
     * Tests connectivity by probing slave IDs {1, 0, 100, 247, 255} with a minimal
     * single-register read at address 30000 (model name).  Reports which slave ID
     * responded so the user can update the config if needed.
     */
    @Override
    public Map<String, Object> testConnection() {
        if (host.isBlank())
            return Map.of("success", false, "message", "Host is required.");

        // First try the configured slave ID
        int[] probeIds = buildProbeIds(slaveId);

        for (int id : probeIds) {
            short[] regs = readRegistersAs(30000, 1, id);
            if (regs != null) {
                // Got a response — read more of the model string for a useful message
                short[] modelRegs = readRegistersAs(30000, 8, id);
                String model = modelRegs != null ? regString(modelRegs) : "Solakon ONE";
                if (model.isBlank()) model = "Solakon ONE";

                String slaveHint = (id != slaveId)
                        ? " (⚠️ working slave ID is " + id
                          + " — update the 'Modbus slave ID' field to " + id + ")"
                        : "";
                return Map.of("success", true,
                        "message", "Connected — " + model + " (slave ID " + id + ")" + slaveHint);
            }
        }

        return Map.of("success", false,
                "message", "No response on " + host + ":" + port
                        + " for any slave ID " + Arrays.toString(probeIds) + ". "
                        + "Port 502 may be open but Modbus TCP might not be enabled yet on the device.");
    }

    /** Puts the configured slave ID first, then the other candidates. */
    private int[] buildProbeIds(int configured) {
        int[] all = new int[PROBE_SLAVE_IDS.length];
        int pos = 0;
        all[pos++] = configured;
        for (int id : PROBE_SLAVE_IDS) {
            if (id != configured) all[pos++] = id;
        }
        return all;
    }

    @Override
    public List<Device> discoverDevices() {
        if (host.isBlank()) return List.of();

        short[] snRegs = readRange(30016, 16);
        String serial  = snRegs != null ? regString(snRegs) : "";
        if (serial.isBlank()) serial = host;

        String name   = "Solakon ONE (" + serial + ")";
        Device device = deviceService.registerDevice(host, name, DeviceType.SOLAKON_ONE, null, instanceId);
        System.out.println("[SolakonONE:" + instanceId + "] Registered: " + name);
        return List.of(device);
    }

    @Override
    public Map<String, Object> getState(String externalId) {
        return readState();
    }

    @Override
    public void sendCommand(String externalId, Map<String, Object> payload) {
        // Read-only for now; remote_active_power (46003) could be added for power limiting
    }

    // ── State reading ─────────────────────────────────────────────────────────

    private Map<String, Object> readState() {
        Map<String, Object> state = new LinkedHashMap<>();

        // ── Block A1: 39053–39082 (30 regs) — rated power + status ──────────
        short[] a1 = readRange(39053, 30);
        if (a1 == null) { state.put("reachable", false); return state; }
        state.put("reachable", true);

        // rated_power @ 39053 [0,1] — i32, scale 1
        int ratedW = i32(a1[0], a1[1]);
        if (ratedW > 0) state.put("rated_power_w", ratedW);

        // status_1 @ 39063 [10] — u16 bitfield
        state.put("status", u16(a1[10]));

        // pv1_voltage @ 39070 [17] — i16, ÷10  pv1_current @ 39071 [18] — ÷100
        state.put("pv1_voltage_v", i16(a1[17]) / 10.0);
        state.put("pv1_current_a", i16(a1[18]) / 100.0);
        // pv2_voltage @ 39072 [19]  pv2_current @ 39073 [20]
        state.put("pv2_voltage_v", i16(a1[19]) / 10.0);
        state.put("pv2_current_a", i16(a1[20]) / 100.0);

        // ── Block A2: 39083–39112 (30 regs) — gap registers ─────────────────
        // (no mapped registers in this range — kept as a placeholder for future use)

        // ── Block A3: 39113–39141 (29 regs) — power, grid, temp ─────────────
        short[] a3 = readRange(39113, 29);
        if (a3 != null) {
            // total_pv_power @ 39118 [5,6] — i32, scale 1
            state.put("pv_power_w",     i32(a3[5],  a3[6]));
            // grid_r_voltage @ 39123 [10] — i16, ÷10
            state.put("grid_voltage_v", i16(a3[10]) / 10.0);
            // active_power   @ 39134 [21,22] — i32, scale 1
            int powerW = i32(a3[21], a3[22]);
            state.put("power_w",        powerW);
            // grid_frequency @ 39139 [26] — i16, ÷100
            state.put("grid_freq_hz",   i16(a3[26]) / 100.0);
            // internal_temp  @ 39141 [28] — i16, ÷10
            state.put("temp_c",         i16(a3[28]) / 10.0);
            state.put("generating",     powerW > 10);
        }

        // ── Block B: 39149–39152 (4 regs) — energy counters ─────────────────
        short[] b = readRange(39149, 4);
        if (b != null) {
            state.put("total_energy_kwh", u32(b[0], b[1]) / 100.0);  // cumulative_generation
            state.put("daily_energy_kwh", u32(b[2], b[3]) / 100.0);  // daily_generation
        }

        // ── Block C: 39279–39282 (4 regs) — per-string power ────────────────
        short[] c = readRange(39279, 4);
        if (c != null) {
            state.put("pv1_power_w", i32(c[0], c[1]));
            state.put("pv2_power_w", i32(c[2], c[3]));
        }

        // ── Block D: 39601–39622 (22 regs) — lifetime + export energy ────────
        short[] d = readRange(39601, 22);
        if (d != null) {
            state.put("pv_total_energy_kwh", u32(d[0],  d[1])  / 100.0);  // @ 39601
            state.put("exported_kwh",        u32(d[20], d[21]) / 100.0);  // @ 39621
        }

        return state;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void poll() {
        try {
            Map<String, Object> state = readState();
            deviceService.getAllDevices().stream()
                    .filter(d -> d.getType() == DeviceType.SOLAKON_ONE
                              && instanceId.equals(d.getIntegrationInstanceId()))
                    .forEach(device -> {
                        try {
                            deviceService.updateState(device.getExternalId(),
                                    objectMapper.writeValueAsString(state));
                            String room = device.getRoom() != null ? device.getRoom() : "solar";
                            storeReading(room, "power_w",          state, device.getExternalId());
                            storeReading(room, "daily_energy_kwh", state, device.getExternalId());
                            storeReading(room, "total_energy_kwh", state, device.getExternalId());
                            broadcaster.broadcastDeviceState(device);
                        } catch (Exception ignored) {}
                    });
        } catch (Exception e) {
            System.err.println("[SolakonONE:" + instanceId + "] Poll failed: " + e.getMessage());
        }
    }

    private void storeReading(String room, String metric,
                              Map<String, Object> state, String externalId) {
        Object v = state.get(metric);
        if (!(v instanceof Number)) return;
        SensorReading r = new SensorReading();
        r.setTopic("solakon/" + externalId.replace(".", "_") + "/" + metric);
        r.setRoom(room);
        r.setMetric(metric);
        r.setValue(((Number) v).doubleValue());
        r.setRecordedAt(Instant.now());
        sensorReadingRepository.save(r);
        broadcaster.broadcastSensorReading(r);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static int parseInt(String s, int fallback) {
        if (s == null || s.isBlank()) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }
}
