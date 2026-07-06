import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { IconKind } from '../deviceIcon'
import { DeviceGlyph } from './DeviceGlyph'

const KINDS: IconKind[] = ['bulb', 'plug', 'solar', 'thermometer', 'humidity', 'power', 'gauge']

describe('DeviceGlyph', () => {
  it.each(KINDS)('renders an svg symbol for the %s kind', (kind) => {
    const { container } = render(<DeviceGlyph kind={kind} />)

    expect(container.querySelectorAll('svg')).toHaveLength(1)
  })
})
