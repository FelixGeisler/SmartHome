package org.felixgeisler.smarthome.integration.homematic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for connecting to the Homematic CCU and discovering its devices. */
@RestController
@RequestMapping("/api/integrations/homematic")
public class HomematicController {

  private final HomematicCcuService ccu;

  /**
   * Creates the controller.
   *
   * @param ccu the Homematic CCU service
   */
  public HomematicController(HomematicCcuService ccu) {
    this.ccu = ccu;
  }

  /**
   * Connects to a CCU with the given WebUI credentials.
   *
   * @param request the CCU host and credentials
   * @return the connection result; {@code connected} is false if the credentials were rejected
   */
  @PostMapping("/connect")
  public ConnectResult connect(@Valid @RequestBody ConnectRequest request) {
    boolean connected = ccu.connect(request.host(), request.username(), request.password());
    String message =
        connected ? "Connected to the CCU." : "The CCU rejected those credentials.";
    return new ConnectResult(connected, message);
  }

  /**
   * Discovers the connected CCU's controllable and sensing channels.
   *
   * @return the discovered devices
   */
  @GetMapping("/devices")
  public List<HomematicDevice> devices() {
    return ccu.discoverDevices();
  }

  /**
   * Reports whether a CCU is currently connected, so the UI can show the connection at a glance.
   *
   * @return the connection status
   */
  @GetMapping("/status")
  public StatusResult status() {
    return new StatusResult(ccu.isConnected());
  }

  /**
   * Request to connect to a CCU.
   *
   * @param host the CCU host (IP or host[:port])
   * @param username the WebUI username
   * @param password the WebUI password
   */
  public record ConnectRequest(
      @NotBlank String host, @NotBlank String username, @NotBlank String password) {}

  /**
   * Status of the Homematic CCU connection.
   *
   * @param connected whether a CCU is currently connected
   */
  public record StatusResult(boolean connected) {}

  /**
   * Result of a connection attempt.
   *
   * @param connected whether the connection succeeded
   * @param message a human-readable explanation
   */
  public record ConnectResult(boolean connected, String message) {}
}
