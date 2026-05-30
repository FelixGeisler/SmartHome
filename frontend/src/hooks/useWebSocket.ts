import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Client } from '@stomp/stompjs'
import { useLiveStore } from '@/store/liveStore'
import { toast } from 'sonner'
import type { Device, SensorReading } from '@/api/client'

let stompClient: Client | null = null
let toastShown = false

export function useWebSocket() {
  const { setWsConnected, setDevices, updateSensorReading } = useLiveStore()
  const qc = useQueryClient()

  useEffect(() => {
    if (stompClient?.active) return

    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const brokerURL = `${proto}//${window.location.host}/ws`

    stompClient = new Client({
      brokerURL,
      reconnectDelay: 8000,

      onConnect: () => {
        setWsConnected(true)
        toastShown = false

        // ── Single device update ─────────────────────────────────────────────
        // Every adapter broadcasts here when a device's state changes.
        // We patch both the device list and the per-device state cache so every
        // widget (LightCard, ThermostatCard, etc.) updates instantly.
        stompClient!.subscribe('/topic/devices/update', (msg) => {
          const device: Device = JSON.parse(msg.body)

          // Patch ['devices'] list in-place
          qc.setQueryData<Device[]>(['devices'], (old) =>
            old
              ? old.map(d => d.id === device.id ? device : d)
              : [device]
          )

          // Patch ['deviceState', id] so widgets that query state separately also update
          if (device.lastStateJson) {
            try {
              const parsed = JSON.parse(device.lastStateJson)
              qc.setQueryData(['deviceState', device.id], (old: Record<string, unknown> | undefined) =>
                old ? { ...old, ...parsed } : parsed
              )
            } catch { /* malformed JSON — ignore */ }
          }
        })

        // ── Full device list refresh (after discovery, etc.) ─────────────────
        stompClient!.subscribe('/topic/devices/all', (msg) => {
          const devices: Device[] = JSON.parse(msg.body)
          setDevices(devices)
          qc.setQueryData(['devices'], devices)
        })

        // ── Sensor reading ───────────────────────────────────────────────────
        // Patched into the Zustand store (used by floor plan / sensor displays)
        // and invalidates sensor history queries so charts re-fetch if open.
        stompClient!.subscribe('/topic/sensors/update', (msg) => {
          const reading: SensorReading = JSON.parse(msg.body)
          updateSensorReading(reading)
          // Invalidate history queries for this room/metric so open charts refresh
          qc.invalidateQueries({ queryKey: ['sensorHistory', reading.room, reading.metric] })
        })
      },

      onDisconnect: () => setWsConnected(false),

      onStompError: () => {
        setWsConnected(false)
        if (!toastShown) {
          toastShown = true
          toast.error('WebSocket connection lost — retrying…')
        }
      },
    })

    stompClient.activate()
    return () => { stompClient?.deactivate(); stompClient = null }
  }, [])
}
