package org.felixgeisler.smarthome.integration.homematic;

import java.util.Map;
import org.felixgeisler.smarthome.capability.AttributeKey;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.springframework.stereotype.Component;

/** Adapter for Homematic command channels reached through the CCU (ADR 2). */
@Component
public class HomematicDeviceAdapter implements DeviceAdapter {

  private static final String STATE = "STATE";

  private final HomematicCcuService ccu;

  /**
   * Creates the adapter.
   *
   * @param ccu the CCU service used to read and write channel datapoints
   */
  public HomematicDeviceAdapter(HomematicCcuService ccu) {
    this.ccu = ccu;
  }

  @Override
  public String adapterType() {
    return "homematic";
  }

  @Override
  public void sendCommand(String externalId, Map<String, Object> payload) {
    if (payload.containsKey(AttributeKey.ON_OFF.wireKey())) {
      boolean on = Boolean.TRUE.equals(payload.get(AttributeKey.ON_OFF.wireKey()));
      ccu.writeValue(externalId, STATE, "boolean", on);
    }
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    return Map.of(AttributeKey.ON_OFF.wireKey(), parseState(ccu.readValue(externalId, STATE)));
  }

  // The CCU reports a boolean datapoint as the string "0"/"1" (or "false"/"true").
  private static boolean parseState(String raw) {
    String value = raw.trim();
    return "1".equals(value) || Boolean.parseBoolean(value);
  }
}
