package org.felixgeisler.smarthome.integration.solakon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for connecting the Solakon inverter integration and reporting its status. */
@RestController
@RequestMapping("/api/integrations/solakon")
public class SolakonController {

  private static final int DEFAULT_PORT = 502;
  private static final int DEFAULT_UNIT_ID = 1;

  private final SolakonConnection connection;

  /**
   * Creates the controller.
   *
   * @param connection the Solakon connection manager
   */
  public SolakonController(SolakonConnection connection) {
    this.connection = connection;
  }

  /**
   * Connects the hub to a Solakon inverter and starts polling its metrics.
   *
   * @param request the inverter host, and optionally the Modbus port and unit id
   * @return the resulting connection status
   */
  @PostMapping("/connect")
  public ConnectionStatus connect(@Valid @RequestBody ConnectRequest request) {
    boolean connected = connection.connect(request.host(), request.port(), request.unitId());
    String message =
        connected
            ? "Connected to the inverter."
            : "Could not reach the inverter; check the host, that it is on wired Ethernet, and "
                + "that Modbus TCP is enabled in the Solakon app.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Disconnects the Solakon integration from its inverter.
   *
   * @return the resulting (disconnected) status
   */
  @PostMapping("/disconnect")
  public ConnectionStatus disconnect() {
    connection.disconnect();
    return new ConnectionStatus(false, "Disconnected from the inverter.");
  }

  /**
   * Reports whether the Solakon integration is currently connected.
   *
   * @return the current connection status
   */
  @GetMapping("/status")
  public ConnectionStatus status() {
    boolean connected = connection.isConnected();
    String message = connected ? "Connected to the inverter." : "Not connected.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Request to connect to a Solakon inverter.
   *
   * @param host the inverter host (IP or hostname)
   * @param port the Modbus TCP port (1-65535); defaults to 502
   * @param unitId the Modbus unit id (1-247); defaults to 1
   */
  public record ConnectRequest(
      @NotBlank String host,
      @Min(1) @Max(65535) Integer port,
      @Min(1) @Max(247) Integer unitId) {

    /** Defaults the port and unit id to the Solakon standards when omitted. */
    public ConnectRequest {
      port = port == null ? Integer.valueOf(DEFAULT_PORT) : port;
      unitId = unitId == null ? Integer.valueOf(DEFAULT_UNIT_ID) : unitId;
    }
  }

  /**
   * Result of a connection request or status query.
   *
   * @param connected whether the integration is connected to an inverter
   * @param message a human-readable explanation
   */
  public record ConnectionStatus(boolean connected, String message) {}
}
