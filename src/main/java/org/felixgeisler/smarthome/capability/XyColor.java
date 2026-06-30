package org.felixgeisler.smarthome.capability;

/**
 * A color as CIE 1931 xy chromaticity.
 *
 * @param x the x chromaticity coordinate (0..1)
 * @param y the y chromaticity coordinate (0..1)
 */
public record XyColor(double x, double y) {

  // sRGB companding threshold below which the transfer function is linear.
  private static final double SRGB_LINEAR_CUTOFF = 0.04045;

  private static final int HEX_RGB_LENGTH = 6;

  // Luminance-sum at or below which the color is black and has no chromaticity.
  private static final double BLACK_LUMINANCE = 0.0;

  /**
   * Converts an sRGB hex color (e.g. {@code "#0000FF"}) to CIE xy chromaticity, so a named or
   * picked color can drive a CIE-xy light. Black has no chromaticity and maps to the origin.
   *
   * @param hex the color as {@code #RRGGBB} or {@code RRGGBB}
   * @return the equivalent chromaticity
   * @throws IllegalArgumentException if the string is not a six-digit hex color
   */
  public static XyColor fromHex(String hex) {
    String digits = hex.startsWith("#") ? hex.substring(1) : hex;
    if (digits.length() != HEX_RGB_LENGTH) {
      throw new IllegalArgumentException("color must be a #RRGGBB hex value, got: " + hex);
    }
    int rgb;
    try {
      rgb = Integer.parseInt(digits, 16);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("color must be a #RRGGBB hex value, got: " + hex, ex);
    }
    double r = linearize(((rgb >> 16) & 0xFF) / 255.0);
    double g = linearize(((rgb >> 8) & 0xFF) / 255.0);
    double b = linearize((rgb & 0xFF) / 255.0);
    // Linear sRGB to CIE XYZ (D65), then normalize XYZ to xy chromaticity.
    double bigX = 0.4124 * r + 0.3576 * g + 0.1805 * b;
    double bigY = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    double bigZ = 0.0193 * r + 0.1192 * g + 0.9505 * b;
    double sum = bigX + bigY + bigZ;
    return sum <= BLACK_LUMINANCE
        ? new XyColor(0.0, 0.0)
        : new XyColor(bigX / sum, bigY / sum);
  }

  // Inverse sRGB gamma: map a 0..1 channel to linear light.
  private static double linearize(double channel) {
    return channel <= SRGB_LINEAR_CUTOFF
        ? channel / 12.92
        : Math.pow((channel + 0.055) / 1.055, 2.4);
  }
}
