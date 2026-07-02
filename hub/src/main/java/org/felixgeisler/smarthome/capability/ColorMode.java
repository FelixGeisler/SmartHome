package org.felixgeisler.smarthome.capability;

/** The active color mode of a color-capable device; exactly one applies at a time. */
public enum ColorMode {
  /** Chromatic color, set via CIE xy. */
  XY,
  /** White, set via color temperature. */
  COLOR_TEMP
}
