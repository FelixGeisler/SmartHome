/**
 * Widget Registry — single source of truth for all dashboard widget types.
 *
 * To add a new widget:
 *   1. Create the widget component in this directory.
 *   2. Add one entry here.
 *   3. Add the type string to WidgetDef['type'] in api/client.ts.
 *   That's it — WidgetPicker and DashboardGrid pick it up automatically.
 */

import { Lightbulb, Thermometer, Home, Plug, Activity, Sun, TrendingUp, Layers, type LucideIcon } from 'lucide-react'
import type { Device, Scene } from '@/api/client'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/client'
import { LightCard } from './LightCard'
import { ThermostatCard } from './ThermostatCard'
import { ShellyPlugCard } from './ShellyPlugCard'
import { SolakonMeterCard } from './SolakonMeterCard'
import { SolakonGatewayCard } from './SolakonGatewayCard'
import { RoomOverview } from './RoomOverview'
import { SensorChartWidget } from './SensorChartWidget'
import { SceneCard } from './SceneCard'

// ── Config-form helpers ───────────────────────────────────────────────────────

const selectCls = 'w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm'
const inputCls  = 'w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm'

function DeviceSelect({ devices, filter, label, value, onChange }: {
  devices: Device[]
  filter: (d: Device) => boolean
  label: string
  value: number
  onChange: (id: number) => void
}) {
  const filtered = devices.filter(filter)
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium">{label}</span>
      <select className={selectCls} value={String(value || '')} onChange={e => onChange(Number(e.target.value))}>
        <option value="">— select —</option>
        {filtered.map(d => (
          <option key={d.id} value={d.id}>
            {d.name}{d.room ? ` (${d.room})` : ''}
          </option>
        ))}
      </select>
    </label>
  )
}

function TextInput({ label, placeholder, value, onChange }: {
  label: string
  placeholder?: string
  value: string
  onChange: (v: string) => void
}) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium">{label}</span>
      <input
        className={inputCls}
        placeholder={placeholder}
        value={value}
        onChange={e => onChange(e.target.value)}
      />
    </label>
  )
}

// ── Config form props ─────────────────────────────────────────────────────────

export interface ConfigFormProps {
  config: Record<string, unknown>
  setConfig: React.Dispatch<React.SetStateAction<Record<string, unknown>>>
  devices: Device[]
}

// ── Registry entry ────────────────────────────────────────────────────────────

export interface WidgetRegistryEntry {
  label: string
  icon: LucideIcon
  desc: string
  /** The widget component rendered inside the dashboard grid tile. */
  Component: React.ComponentType<{ config: Record<string, unknown> }>
  /** Form rendered inside WidgetPicker when the user configures a new widget. */
  ConfigForm: React.ComponentType<ConfigFormProps>
}

// ── Registry ──────────────────────────────────────────────────────────────────

export const WIDGET_REGISTRY = {
  LightCard: {
    label: 'Light',
    icon: Lightbulb,
    desc: 'Toggle & dim a Hue lamp',
    Component: LightCard,
    ConfigForm: ({ config, setConfig, devices }: ConfigFormProps) => (
      <DeviceSelect
        devices={devices}
        filter={d => d.type === 'HUE_LIGHT'}
        label="Hue Light"
        value={Number(config.deviceId ?? 0)}
        onChange={id => setConfig(prev => ({ ...prev, deviceId: id }))}
      />
    ),
  },

  ThermostatCard: {
    label: 'Thermostat',
    icon: Thermometer,
    desc: 'Control a Homematic radiator',
    Component: ThermostatCard,
    ConfigForm: ({ config, setConfig, devices }: ConfigFormProps) => (
      <DeviceSelect
        devices={devices}
        filter={d => d.type === 'HOMEMATIC_RADIATOR'}
        label="Homematic Radiator"
        value={Number(config.deviceId ?? 0)}
        onChange={id => setConfig(prev => ({ ...prev, deviceId: id }))}
      />
    ),
  },

  ShellyPlugCard: {
    label: 'Shelly Plug',
    icon: Plug,
    desc: 'Toggle a Shelly smart plug',
    Component: ShellyPlugCard,
    ConfigForm: ({ config, setConfig, devices }: ConfigFormProps) => (
      <DeviceSelect
        devices={devices}
        filter={d => d.type === 'SHELLY_PLUG'}
        label="Shelly Plug"
        value={Number(config.deviceId ?? 0)}
        onChange={id => setConfig(prev => ({ ...prev, deviceId: id }))}
      />
    ),
  },

  SolakonMeterCard: {
    label: 'Energy Meter',
    icon: Activity,
    desc: 'Live grid power & energy totals (IR01)',
    Component: SolakonMeterCard,
    ConfigForm: ({ config, setConfig, devices }: ConfigFormProps) => (
      <DeviceSelect
        devices={devices}
        filter={d => d.type === 'SOLAKON_METER'}
        label="IR01 Energy Meter"
        value={Number(config.deviceId ?? 0)}
        onChange={id => setConfig(prev => ({ ...prev, deviceId: id }))}
      />
    ),
  },

  SolakonGatewayCard: {
    label: 'Solar Gateway',
    icon: Sun,
    desc: 'Solakon GW-M1 micro-inverter status',
    Component: SolakonGatewayCard,
    ConfigForm: ({ config, setConfig, devices }: ConfigFormProps) => (
      <DeviceSelect
        devices={devices}
        filter={d => d.type === 'SOLAKON_INVERTER'}
        label="Solar Gateway (GW-M1)"
        value={Number(config.deviceId ?? 0)}
        onChange={id => setConfig(prev => ({ ...prev, deviceId: id }))}
      />
    ),
  },

  RoomOverview: {
    label: 'Room Overview',
    icon: Home,
    desc: 'All devices in one room',
    Component: RoomOverview,
    ConfigForm: ({ config, setConfig }: ConfigFormProps) => (
      <TextInput
        label="Room"
        placeholder="living-room"
        value={String(config.room ?? '')}
        onChange={v => setConfig(prev => ({ ...prev, room: v }))}
      />
    ),
  },

  SensorChart: {
    label: 'Sensor Chart',
    icon: TrendingUp,
    desc: 'Temperature / humidity history graph',
    Component: SensorChartWidget,
    ConfigForm: ({ config, setConfig }: ConfigFormProps) => {
      const { data: readings = [] } = useQuery({
        queryKey: ['sensors-latest'],
        queryFn: api.sensors.latest,
      })
      const room    = String(config.room ?? '')
      const sources = Array.from(new Set(readings.map(r => r.room))).sort()
      // Only offer metrics that actually have data for the selected source
      const metrics = Array.from(new Set(
        readings.filter(r => !room || r.room === room).map(r => r.metric)
      )).sort()
      return (
        <div className="space-y-4">
          <label className="block space-y-1.5">
            <span className="text-sm font-medium">Source</span>
            <select
              className={selectCls}
              value={room}
              onChange={e => setConfig(prev => ({ ...prev, room: e.target.value }))}
            >
              <option value="">— select —</option>
              {sources.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label className="block space-y-1.5">
            <span className="text-sm font-medium">Metric</span>
            <select
              className={selectCls}
              value={String(config.metric ?? '')}
              onChange={e => setConfig(prev => ({ ...prev, metric: e.target.value }))}
              disabled={metrics.length === 0}
            >
              <option value="">— select —</option>
              {metrics.map(m => <option key={m} value={m}>{m}</option>)}
            </select>
          </label>
          <label className="block space-y-1.5">
            <span className="text-sm font-medium">Default time range</span>
            <select
              className={selectCls}
              value={String(config.hours ?? '24')}
              onChange={e => setConfig(prev => ({ ...prev, hours: Number(e.target.value) }))}
            >
              <option value="1">1 hour</option>
              <option value="6">6 hours</option>
              <option value="24">24 hours</option>
              <option value="168">7 days</option>
            </select>
          </label>
        </div>
      )
    },
  },
  SceneCard: {
    label: 'Scene',
    icon: Layers,
    desc: 'One-tap button to activate a scene',
    Component: SceneCard,
    ConfigForm: ({ config, setConfig }: ConfigFormProps) => {
      const { data: scenes = [] } = useQuery<Scene[]>({
        queryKey: ['scenes'],
        queryFn: api.scenes.list,
      })
      return (
        <label className="block space-y-1.5">
          <span className="text-sm font-medium">Scene</span>
          <select
            className="w-full px-3 py-2 rounded-lg border border-[hsl(var(--border))] bg-[hsl(var(--background))] text-sm"
            value={String(config.sceneId ?? '')}
            onChange={e => setConfig(prev => ({ ...prev, sceneId: Number(e.target.value) }))}
          >
            <option value="">— select —</option>
            {scenes.map(s => (
              <option key={s.id} value={s.id}>{s.icon} {s.name}</option>
            ))}
          </select>
        </label>
      )
    },
  },
} as const satisfies Record<string, WidgetRegistryEntry>

/** Union of all registered widget type strings. */
export type WidgetType = keyof typeof WIDGET_REGISTRY
