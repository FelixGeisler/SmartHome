/**
 * Color math for the color picker: convert between the browser's sRGB hex colors and the
 * device-neutral CIE 1931 xy chromaticity the API speaks. Uses the standard sRGB primaries
 * and the D65 white point.
 */

/**
 * Converts an sRGB hex color (e.g. {@code "#ff8800"}) to CIE xy chromaticity.
 *
 * @param hex the color as a {@code #rrggbb} string
 * @returns the chromaticity, with x and y in 0..1
 */
export function hexToXy(hex: string): { x: number; y: number } {
  const { r, g, b } = toLinearRgb(hex)
  const cieX = 0.4124 * r + 0.3576 * g + 0.1805 * b
  const cieY = 0.2126 * r + 0.7152 * g + 0.0722 * b
  const cieZ = 0.0193 * r + 0.1192 * g + 0.9505 * b
  const sum = cieX + cieY + cieZ
  if (sum === 0) {
    return { x: 0, y: 0 }
  }
  return { x: round(cieX / sum), y: round(cieY / sum) }
}

/**
 * Converts a CIE xy chromaticity to an sRGB hex color for display. Out-of-gamut colors are
 * scaled back into range, so the swatch keeps the hue even when the exact color is not displayable.
 *
 * @param x the x chromaticity coordinate (0..1)
 * @param y the y chromaticity coordinate (0..1)
 * @returns the color as a {@code #rrggbb} string
 */
export function xyToHex(x: number, y: number): string {
  if (y === 0) {
    return '#000000'
  }
  const cieY = 1
  const cieX = (cieY / y) * x
  const cieZ = (cieY / y) * (1 - x - y)
  const r = 3.2406 * cieX - 1.5372 * cieY - 0.4986 * cieZ
  const g = -0.9689 * cieX + 1.8758 * cieY + 0.0415 * cieZ
  const b = 0.0557 * cieX - 0.204 * cieY + 1.057 * cieZ
  // Scale so the brightest channel is at most 1, preserving the ratio (hue) between channels.
  const peak = Math.max(r, g, b, 1)
  return `#${[r, g, b].map((channel) => toHexByte(gammaEncode(channel / peak))).join('')}`
}

function toLinearRgb(hex: string): { r: number; g: number; b: number } {
  const digits = hex.replace('#', '')
  const r = parseInt(digits.slice(0, 2), 16) / 255
  const g = parseInt(digits.slice(2, 4), 16) / 255
  const b = parseInt(digits.slice(4, 6), 16) / 255
  return { r: gammaDecode(r), g: gammaDecode(g), b: gammaDecode(b) }
}

function gammaDecode(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4)
}

function gammaEncode(channel: number): number {
  const encoded =
    channel <= 0.0031308 ? 12.92 * channel : 1.055 * Math.pow(channel, 1 / 2.4) - 0.055
  return Math.min(1, Math.max(0, encoded))
}

function toHexByte(channel: number): string {
  return Math.round(channel * 255)
    .toString(16)
    .padStart(2, '0')
}

function round(value: number): number {
  return Math.round(value * 10000) / 10000
}
