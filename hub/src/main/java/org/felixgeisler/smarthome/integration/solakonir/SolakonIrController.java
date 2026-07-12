package org.felixgeisler.smarthome.integration.solakonir;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for connecting the Solakon IR meter integration and reporting its status. */
@RestController
@RequestMapping("/api/integrations/solakon-ir")
public class SolakonIrController {

  private final SolakonIrConnection connection;

  /**
   * Creates the controller.
   *
   * @param connection the Solakon IR meter connection manager
   */
  public SolakonIrController(SolakonIrConnection connection) {
    this.connection = connection;
  }

  /**
   * Connects the hub to a Solakon IR meter head and starts polling its grid metering.
   *
   * @param request the meter host
   * @return the resulting connection status
   */
  @PostMapping("/connect")
  public ConnectionStatus connect(@Valid @RequestBody ConnectRequest request) {
    boolean connected = connection.connect(request.host());
    String message =
        connected
            ? "Connected to the meter."
            : "Could not reach the meter; check the host and that the IR head is on the network.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Disconnects the Solakon IR meter integration from its meter head.
   *
   * @return the resulting (disconnected) status
   */
  @PostMapping("/disconnect")
  public ConnectionStatus disconnect() {
    connection.disconnect();
    return new ConnectionStatus(false, "Disconnected from the meter.");
  }

  /**
   * Reports whether the Solakon IR meter integration is currently connected.
   *
   * @return the current connection status
   */
  @GetMapping("/status")
  public ConnectionStatus status() {
    boolean connected = connection.isConnected();
    String message = connected ? "Connected to the meter." : "Not connected.";
    return new ConnectionStatus(connected, message);
  }

  /**
   * Request to connect to a Solakon IR meter head.
   *
   * @param host the meter host (IP or host[:port])
   */
  public record ConnectRequest(@NotBlank String host) {}

  /**
   * Result of a connection request or status query.
   *
   * @param connected whether the integration is connected to a meter
   * @param message a human-readable explanation
   */
  public record ConnectionStatus(boolean connected, String message) {}
}
