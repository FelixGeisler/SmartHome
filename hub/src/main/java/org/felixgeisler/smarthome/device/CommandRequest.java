package org.felixgeisler.smarthome.device;

import org.felixgeisler.smarthome.capability.XyColor;

/**
 * Request body for a neutral device command (ADR 3). Every field is optional; a command sets only
 * the attributes it carries, and at least one must be present.
 *
 * <p>Shape and range are not validated here: the {@link org.felixgeisler.smarthome.capability
 * neutral contract} enforces validity once, in {@code AttributeKey}, so the rules are not
 * duplicated. Setting both {@link #colorXy()} and {@link #colorTemperatureK()} is rejected because
 * a color device is in one color mode at a time.
 *
 * @param on the desired power state, or null to leave it unchanged
 * @param brightness the desired brightness percentage, or null to leave it unchanged
 * @param colorXy the desired color as CIE xy, or null to leave it unchanged
 * @param colorTemperatureK the desired color temperature in Kelvin, or null to leave it unchanged
 */
public record CommandRequest(
    Boolean on, Integer brightness, XyColor colorXy, Integer colorTemperatureK) {}
