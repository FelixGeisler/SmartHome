package org.felixgeisler.smarthome.device;

import org.felixgeisler.smarthome.capability.XyColor;

/**
 * Request body for a neutral device command (ADR 3).
 *
 * <p>Validity is enforced once in {@code AttributeKey}, not here; xy and Kelvin are mutually
 * exclusive because a color device is in one color mode at a time.
 *
 * @param on the desired power state, or null to leave unchanged
 * @param brightness the desired brightness percentage, or null to leave unchanged
 * @param colorXy the desired color as CIE xy, or null to leave unchanged
 * @param colorTemperatureK the desired color temperature in Kelvin, or null to leave unchanged
 */
public record CommandRequest(
    Boolean on, Integer brightness, XyColor colorXy, Integer colorTemperatureK) {}
