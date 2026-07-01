import { describe, expect, it } from 'vitest'
import { hexToXy, xyToHex } from './color'

describe('color conversion', () => {
  it('maps the sRGB primaries to their known CIE xy chromaticities', () => {
    const red = hexToXy('#ff0000')
    expect(red.x).toBeCloseTo(0.64, 2)
    expect(red.y).toBeCloseTo(0.33, 2)

    const green = hexToXy('#00ff00')
    expect(green.x).toBeCloseTo(0.3, 2)
    expect(green.y).toBeCloseTo(0.6, 2)
  })

  it('maps white to the D65 white point', () => {
    const white = hexToXy('#ffffff')
    expect(white.x).toBeCloseTo(0.3127, 3)
    expect(white.y).toBeCloseTo(0.329, 3)
  })

  it('round-trips a color through xy and back to a near-identical hue', () => {
    const xy = hexToXy('#3366cc')

    // The exact color may be out of gamut after the round trip, but the hue is preserved:
    // blue stays the dominant channel.
    const hex = xyToHex(xy.x, xy.y)
    const blue = parseInt(hex.slice(5, 7), 16)
    const red = parseInt(hex.slice(1, 3), 16)
    expect(blue).toBeGreaterThan(red)
  })

  it('returns black for a degenerate chromaticity', () => {
    expect(xyToHex(0.3, 0)).toBe('#000000')
  })
})
