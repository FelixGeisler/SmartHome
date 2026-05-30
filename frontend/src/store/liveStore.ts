import { create } from 'zustand'
import type { Device, SensorReading } from '@/api/client'

interface LiveState {
  // Live device states keyed by device id
  deviceStates: Record<number, Record<string, unknown>>
  updateDeviceState: (id: number, state: Record<string, unknown>) => void

  // Latest sensor readings keyed by "room/metric"
  sensorReadings: Record<string, SensorReading>
  updateSensorReading: (reading: SensorReading) => void

  // All devices (refreshed on WS broadcast)
  devices: Device[]
  setDevices: (devices: Device[]) => void

  // WS connection status
  wsConnected: boolean
  setWsConnected: (v: boolean) => void

  // Dark mode
  darkMode: boolean
  toggleDarkMode: () => void
}

export const useLiveStore = create<LiveState>((set, get) => ({
  deviceStates: {},
  updateDeviceState: (id, state) =>
    set(s => ({ deviceStates: { ...s.deviceStates, [id]: state } })),

  sensorReadings: {},
  updateSensorReading: (reading) =>
    set(s => ({
      sensorReadings: {
        ...s.sensorReadings,
        [`${reading.room}/${reading.metric}`]: reading,
      },
    })),

  devices: [],
  setDevices: (devices) => set({ devices }),

  wsConnected: false,
  setWsConnected: (v) => set({ wsConnected: v }),

  darkMode: localStorage.getItem('darkMode') === 'true',
  toggleDarkMode: () => {
    const next = !get().darkMode
    localStorage.setItem('darkMode', String(next))
    document.documentElement.classList.toggle('dark', next)
    set({ darkMode: next })
  },
}))

// Apply persisted dark mode on load
if (localStorage.getItem('darkMode') === 'true') {
  document.documentElement.classList.add('dark')
}
