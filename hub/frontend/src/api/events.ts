import type { Device } from './devices'

/** Callbacks driven by the hub's live device event stream. */
export interface DeviceStreamHandlers {
  /** A device was added or changed; the payload is its current view. */
  onDeviceChanged: (device: Device) => void
  /** A device was removed; the payload is its id. */
  onDeviceRemoved: (id: number) => void
  /**
   * The stream (re)connected; fires on every open, including the first. Events emitted while the
   * stream was down are not redelivered, so this is the signal to re-sync from the REST API.
   */
  onSync: () => void
}

/** How long to wait before rebuilding a stream the browser has given up on. */
const RETRY_MS = 5000

/**
 * Subscribes to the hub's live device event stream (Server-Sent Events at {@code /api/events}) so
 * the dashboard reflects changes without polling. The browser's EventSource reconnects on its own
 * after a network drop, but gives up for good when a reconnect attempt gets an HTTP error response
 * (e.g. a proxy answering 502 while the hub restarts); in that case the stream is rebuilt from
 * scratch after a short pause, so the dashboard always comes back on its own.
 *
 * @param handlers callbacks for the stream's events
 * @returns a function that closes the stream and stops any pending rebuild
 */
export function openDeviceStream(handlers: DeviceStreamHandlers): () => void {
  let source: EventSource | null = null
  let retryTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false

  function connect() {
    // Bind every handler to this connection's own instance, not the mutable `source`, so a rebuilt
    // stream can never be closed or retried by a handler left over from the connection it replaced.
    const es = new EventSource('/api/events')
    source = es
    es.onopen = () => handlers.onSync()
    es.onerror = () => {
      // While CONNECTING the browser is already retrying by itself; only a CLOSED
      // stream is dead for good and needs to be rebuilt.
      if (!disposed && es.readyState === EventSource.CLOSED) {
        es.close()
        retryTimer = setTimeout(connect, RETRY_MS)
      }
    }
    es.addEventListener('device-changed', (event) => {
      handlers.onDeviceChanged(JSON.parse((event as MessageEvent).data) as Device)
    })
    es.addEventListener('device-removed', (event) => {
      const { id } = JSON.parse((event as MessageEvent).data) as { id: number }
      handlers.onDeviceRemoved(id)
    })
  }

  connect()
  return () => {
    disposed = true
    clearTimeout(retryTimer)
    source?.close()
  }
}
