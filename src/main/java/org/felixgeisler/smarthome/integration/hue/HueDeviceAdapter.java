package org.felixgeisler.smarthome.integration.hue;

import java.util.Map;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.springframework.stereotype.Component;

/**
 * Controls Philips Hue lights through the bridge, addressing each light by its bridge light id
 * ({@code externalId}). Delegates the bridge connection and HTTP to {@link HueBridgeService}.
 */
@Component
public class HueDeviceAdapter implements DeviceAdapter {

  private final HueBridgeService bridge;

  /**
   * Creates the adapter.
   *
   * @param bridge the Hue bridge service
   */
  public HueDeviceAdapter(HueBridgeService bridge) {
    this.bridge = bridge;
  }

  @Override
  public String adapterType() {
    return "hue";
  }

  @Override
  public void sendCommand(String externalId, Map<String, Object> payload) {
    bridge.setLightOn(externalId, Boolean.TRUE.equals(payload.get("on")));
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    return Map.of("on", bridge.getLightOn(externalId));
  }
}
