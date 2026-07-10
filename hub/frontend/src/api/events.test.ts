import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { openDeviceStream, type DeviceStreamHandlers } from './events'

class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2
  static instances: FakeEventSource[] = []

  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  readyState = FakeEventSource.CONNECTING
  closed = false
  readonly url: string
  private readonly listeners = new Map<string, (event: MessageEvent) => void>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  static get last(): FakeEventSource {
    return FakeEventSource.instances[FakeEventSource.instances.length - 1]
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listeners.set(type, listener)
  }

  emit(type: string, data: unknown) {
    this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent)
  }

  open() {
    this.readyState = FakeEventSource.OPEN
    this.onopen?.()
  }

  /** The browser gave up: readyState CLOSED plus an error event. */
  die() {
    this.readyState = FakeEventSource.CLOSED
    this.onerror?.()
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }
}

function handlers(overrides: Partial<DeviceStreamHandlers> = {}): DeviceStreamHandlers {
  return { onDeviceChanged: vi.fn(), onDeviceRemoved: vi.fn(), onSync: vi.fn(), ...overrides }
}

describe('openDeviceStream', () => {
  beforeEach(() => {
    FakeEventSource.instances = []
    vi.stubGlobal('EventSource', FakeEventSource)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('connects to the hub event endpoint', () => {
    openDeviceStream(handlers())

    expect(FakeEventSource.last.url).toBe('/api/events')
  })

  it('parses a device-changed event into a device', () => {
    const onDeviceChanged = vi.fn()
    openDeviceStream(handlers({ onDeviceChanged }))

    FakeEventSource.last.emit('device-changed', { id: 1, name: 'Desk Lamp' })

    expect(onDeviceChanged).toHaveBeenCalledWith({ id: 1, name: 'Desk Lamp' })
  })

  it('parses a device-removed event into an id', () => {
    const onDeviceRemoved = vi.fn()
    openDeviceStream(handlers({ onDeviceRemoved }))

    FakeEventSource.last.emit('device-removed', { id: 7 })

    expect(onDeviceRemoved).toHaveBeenCalledWith(7)
  })

  it('signals a sync on every open, including the first', () => {
    const onSync = vi.fn()
    openDeviceStream(handlers({ onSync }))

    FakeEventSource.last.open()
    expect(onSync).toHaveBeenCalledTimes(1)

    FakeEventSource.last.open()
    expect(onSync).toHaveBeenCalledTimes(2)
  })

  it('rebuilds the stream after the browser gives up on it', () => {
    const onSync = vi.fn()
    openDeviceStream(handlers({ onSync }))

    // An HTTP error on reconnect closes the stream for good; the browser stops retrying.
    FakeEventSource.last.die()
    expect(FakeEventSource.instances).toHaveLength(1)

    vi.advanceTimersByTime(5000)
    expect(FakeEventSource.instances).toHaveLength(2)

    FakeEventSource.last.open()
    expect(onSync).toHaveBeenCalledTimes(1)
  })

  it('leaves a transient connection error to the browser instead of rebuilding', () => {
    openDeviceStream(handlers())

    // While CONNECTING the browser retries by itself; a rebuild would double the streams.
    FakeEventSource.last.onerror?.()
    vi.advanceTimersByTime(60_000)

    expect(FakeEventSource.instances).toHaveLength(1)
  })

  it('closes a live stream when the disposer is called', () => {
    const close = openDeviceStream(handlers())
    FakeEventSource.last.open()

    close()

    expect(FakeEventSource.last.closed).toBe(true)
  })

  it('stops rebuilding when the disposer is called during the retry pause', () => {
    const close = openDeviceStream(handlers())
    FakeEventSource.last.die()

    close()
    vi.advanceTimersByTime(60_000)

    expect(FakeEventSource.instances).toHaveLength(1)
  })
})
