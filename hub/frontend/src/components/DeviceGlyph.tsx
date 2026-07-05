import type { IconKind } from '../deviceIcon'

/** Shared attributes for the floor-plan glyphs; line art in the toolbar/card convention. */
const GLYPH = {
  viewBox: '0 0 24 24',
  width: 24,
  height: 24,
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
  focusable: false,
} as const

/** Renders the inline SVG symbol for a device kind; the accessible name lives on the wrapper. */
export function DeviceGlyph({ kind }: { kind: IconKind }) {
  switch (kind) {
    case 'bulb':
      return <BulbIcon />
    case 'plug':
      return <PlugIcon />
    case 'solar':
      return <SunIcon />
    case 'thermometer':
      return <ThermometerIcon />
    case 'humidity':
      return <DropletIcon />
    case 'power':
      return <BoltIcon />
    case 'gauge':
      return <GaugeIcon />
    default:
      return <GaugeIcon />
  }
}

function BulbIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M9 18h6" />
      <path d="M10 21h4" />
      <path d="M12 3a6 6 0 0 0-4 10.5c.7.7 1 1.3 1 2.5h6c0-1.2.3-1.8 1-2.5A6 6 0 0 0 12 3Z" />
    </svg>
  )
}

function PlugIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M9 2v6" />
      <path d="M15 2v6" />
      <path d="M7 8h10v3a5 5 0 0 1-10 0V8Z" />
      <path d="M12 16v5" />
    </svg>
  )
}

function SunIcon() {
  return (
    <svg {...GLYPH}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2" />
      <path d="M12 20v2" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="M4.9 4.9l1.4 1.4" />
      <path d="M17.7 17.7l1.4 1.4" />
      <path d="M4.9 19.1l1.4-1.4" />
      <path d="M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

function ThermometerIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M14 14.76V5a2 2 0 1 0-4 0v9.76a4 4 0 1 0 4 0Z" />
    </svg>
  )
}

function DropletIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M12 3s6 6.5 6 10a6 6 0 0 1-12 0c0-3.5 6-10 6-10Z" />
    </svg>
  )
}

function BoltIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M13 2 4 14h7l-1 8 9-12h-7l1-8Z" />
    </svg>
  )
}

function GaugeIcon() {
  return (
    <svg {...GLYPH}>
      <path d="M4 18a8 8 0 1 1 16 0" />
      <path d="M12 12l3-3" />
      <circle cx="12" cy="12" r="1.4" />
    </svg>
  )
}
