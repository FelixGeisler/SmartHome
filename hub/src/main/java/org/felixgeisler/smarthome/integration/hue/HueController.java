package org.felixgeisler.smarthome.integration.hue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for pairing with the Hue bridge and discovering its lights. */
@RestController
@RequestMapping("/api/integrations/hue")
public class HueController {

  private final HueBridgeService bridge;

  /**
   * Creates the controller.
   *
   * @param bridge the Hue bridge service
   */
  public HueController(HueBridgeService bridge) {
    this.bridge = bridge;
  }

  /**
   * Pairs with a bridge. The bridge link button must be pressed first.
   *
   * @param request the bridge host to pair with
   * @return the pairing result; {@code paired} is false if the link button was not pressed
   */
  @PostMapping("/pair")
  public PairResult pair(@Valid @RequestBody PairRequest request) {
    boolean paired = bridge.pair(request.host());
    String message =
        paired ? "Paired with the bridge." : "Press the bridge link button, then try again.";
    return new PairResult(paired, message);
  }

  /**
   * Lists the lights on the paired bridge.
   *
   * @return the discovered lights
   */
  @GetMapping("/lights")
  public List<HueLight> lights() {
    return bridge.discoverLights();
  }

  /**
   * Request to pair with a bridge.
   *
   * @param host the bridge host (IP or host[:port])
   */
  public record PairRequest(@NotBlank String host) {}

  /**
   * Result of a pairing attempt.
   *
   * @param paired whether pairing succeeded
   * @param message a human-readable explanation
   */
  public record PairResult(boolean paired, String message) {}
}
