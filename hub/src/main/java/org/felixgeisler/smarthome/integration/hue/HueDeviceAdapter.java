package org.felixgeisler.smarthome.integration.hue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.felixgeisler.smarthome.capability.AttributeKey;
import org.felixgeisler.smarthome.capability.ColorMode;
import org.felixgeisler.smarthome.capability.XyColor;
import org.felixgeisler.smarthome.integration.DeviceAdapter;
import org.springframework.stereotype.Component;

/**
 * Controls Philips Hue lights through the bridge, addressing each light by its bridge light id
 * ({@code externalId}). Delegates the bridge connection and HTTP to {@link HueBridgeService}.
 *
 * <p>This adapter is the only place the neutral contract (ADR 3) meets Hue's native encoding:
 * brightness percentage to {@code bri} 1..254, color temperature in Kelvin to mireds, and CIE xy
 * to the bridge's {@code xy} pair, and back again.
 */
@Component
public class HueDeviceAdapter implements DeviceAdapter {

  /** Hue's brightest native value; brightness 1 maps to 1, not 0 (0 is not the off state). */
  private static final int HUE_MAX_BRI = 254;

  private static final int PERCENT_MAX = 100;

  /** Hue's mired bounds, ~6500K to ~2000K; the contract's Kelvin is clamped into this window. */
  private static final int MIRED_MIN = 153;

  private static final int MIRED_MAX = 500;

  /** Kelvin and mireds are reciprocals scaled by a million (mired = 1e6 / Kelvin). */
  private static final double KELVIN_MIRED_SCALE = 1_000_000.0;

  /** Hue's {@code colormode} values: color temperature, CIE xy, and hue/saturation. */
  private static final String HUE_MODE_CT = "ct";

  private static final String HUE_MODE_XY = "xy";

  private static final String HUE_MODE_HS = "hs";

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
    bridge.setLightState(externalId, toNative(payload));
  }

  @Override
  public Map<String, Object> getState(String externalId) {
    return toNeutral(bridge.getLight(externalId));
  }

  // Translate a neutral payload (wire keys to neutral values) into a Hue state body.
  private static Map<String, Object> toNative(Map<String, Object> neutral) {
    Map<String, Object> state = new LinkedHashMap<>();
    Object on = neutral.get(AttributeKey.ON_OFF.wireKey());
    if (on instanceof Boolean flag) {
      state.put("on", flag);
    }
    Object brightness = neutral.get(AttributeKey.BRIGHTNESS.wireKey());
    if (brightness instanceof Integer percent) {
      state.put("bri", toBri(percent));
    }
    Object color = neutral.get(AttributeKey.COLOR_XY.wireKey());
    if (color instanceof XyColor(double x, double y)) {
      state.put("xy", List.of(x, y));
    }
    Object temperature = neutral.get(AttributeKey.COLOR_TEMPERATURE_K.wireKey());
    if (temperature instanceof Integer kelvin) {
      state.put("ct", toMireds(kelvin));
    }
    return state;
  }

  // Translate the bridge's native state back into neutral attributes for the dashboard.
  private static Map<String, Object> toNeutral(HueLightResource light) {
    Map<String, Object> neutral = new LinkedHashMap<>();
    HueLightResource.State state = light.state();
    if (state == null) {
      return neutral;
    }
    neutral.put(AttributeKey.ON_OFF.wireKey(), state.on());
    if (state.bri() != null) {
      neutral.put(AttributeKey.BRIGHTNESS.wireKey(), toPercent(state.bri()));
    }
    if (state.xy() != null && state.xy().size() == 2) {
      neutral.put(
          AttributeKey.COLOR_XY.wireKey(), new XyColor(state.xy().get(0), state.xy().get(1)));
    }
    if (state.ct() != null) {
      neutral.put(AttributeKey.COLOR_TEMPERATURE_K.wireKey(), toKelvin(state.ct()));
    }
    ColorMode mode = toColorMode(state.colormode());
    if (mode != null) {
      neutral.put(AttributeKey.COLOR_MODE.wireKey(), mode);
    }
    return neutral;
  }

  private static int toBri(int percent) {
    return Math.clamp(Math.round(percent * (float) HUE_MAX_BRI / PERCENT_MAX), 1, HUE_MAX_BRI);
  }

  private static int toPercent(int bri) {
    return Math.clamp(Math.round(bri * (float) PERCENT_MAX / HUE_MAX_BRI), 1, PERCENT_MAX);
  }

  private static int toMireds(int kelvin) {
    return Math.clamp((int) Math.round(KELVIN_MIRED_SCALE / kelvin), MIRED_MIN, MIRED_MAX);
  }

  private static int toKelvin(int mireds) {
    return (int) Math.round(KELVIN_MIRED_SCALE / mireds);
  }

  private static ColorMode toColorMode(String hueMode) {
    if (HUE_MODE_CT.equals(hueMode)) {
      return ColorMode.COLOR_TEMP;
    }
    if (HUE_MODE_XY.equals(hueMode) || HUE_MODE_HS.equals(hueMode)) {
      return ColorMode.XY;
    }
    return null;
  }

}
