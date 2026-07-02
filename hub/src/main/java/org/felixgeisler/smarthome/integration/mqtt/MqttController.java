package org.felixgeisler.smarthome.integration.mqtt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for connecting the MQTT broker integration and reporting its status. */
@RestController
@RequestMapping("/api/integrations/mqtt")
public class MqttController {

  private final MqttConnection connection;

  /**
   * Creates the controller.
   *
   * @param connection the MQTT connection manager
   */
  public MqttController(MqttConnection connection) {
    this.connection = connection;
  }

  /**
   * Connects the hub to an MQTT broker and subscribes to sensor telemetry.
   *
   * @param request the broker host and port to connect to
   * @return the resulting connection status
   */
  @PostMapping("/connect")
  public ConnectionStatus connect(@Valid @RequestBody ConnectRequest request) {
    boolean connected = connection.connect(request.host(), request.port());
    String message =
        connected
            ? "Connected to the broker."
            : "Could not reach the broker; check the host and port and that it is running.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Disconnects the MQTT integration from its broker.
   *
   * @return the resulting (disconnected) status
   */
  @PostMapping("/disconnect")
  public ConnectionStatus disconnect() {
    connection.disconnect();
    return new ConnectionStatus(false, "Disconnected from the broker.");
  }

  /**
   * Reports whether the MQTT integration is currently connected to a broker.
   *
   * @return the current connection status
   */
  @GetMapping("/status")
  public ConnectionStatus status() {
    boolean connected = connection.isConnected();
    String message = connected ? "Connected to the broker." : "Not connected.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Request to connect to a broker.
   *
   * @param host the broker host (IP or hostname)
   * @param port the broker port (1-65535); defaults to 1883 when omitted
   */
  public record ConnectRequest(@NotBlank String host, @Min(1) @Max(65535) Integer port) {

    /** Defaults the port to the MQTT standard 1883 when the request omits it. */
    public ConnectRequest {
      port = port == null ? Integer.valueOf(1883) : port;
    }
  }

  /**
   * Result of a connection request or a status query.
   *
   * @param connected whether the integration is connected to a broker
   * @param message a human-readable explanation
   */
  public record ConnectionStatus(boolean connected, String message) {}
}
