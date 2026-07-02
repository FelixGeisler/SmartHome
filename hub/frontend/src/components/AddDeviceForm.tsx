import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Device, DeviceRegistration, SensorSpec } from '../api/devices'
import { registerDevice } from '../api/devices'

// Each kind pairs the API's device type with how the device is reached: a command adapter for
// switchable devices, or a set of declared sensors for sensing devices.
type DeviceKind = {
  label: string
  type: string
  addressLabel: string
  addressPlaceholder: string
  adapterType?: string
  sensing?: boolean
}

const DEVICE_KINDS: DeviceKind[] = [
  {
    label: 'Shelly Plug',
    type: 'SHELLY_PLUG',
    addressLabel: 'Host',
    addressPlaceholder: '192.168.1.50',
    adapterType: 'shelly',
  },
  {
    label: 'Sensor Node (MQTT)',
    type: 'SENSOR_NODE',
    addressLabel: 'Node ID',
    addressPlaceholder: 'living-room',
    sensing: true,
  },
]

// Presets so picking a sensor type fills in a sensible key and unit (still editable).
const SENSOR_PRESETS: SensorSpec[] = [
  { type: 'TEMPERATURE', key: 'temperature', unit: '°C' },
  { type: 'HUMIDITY', key: 'humidity', unit: '%' },
  { type: 'PRESSURE', key: 'pressure', unit: 'hPa' },
  { type: 'CO2', key: 'co2', unit: 'ppm' },
]

// A stable per-row id so React keys survive add/remove. UI-only; never sent to the API.
type SensorRow = SensorSpec & { id: string }
let nextSensorId = 0

function defaultSensor(): SensorRow {
  return { id: `sensor-${nextSensorId++}`, ...SENSOR_PRESETS[0] }
}

interface AddDeviceFormProps {
  onRegistered: (device: Device) => void
}

/** Registration form: device name, address, kind, and (for sensor nodes) its declared sensors. */
export function AddDeviceForm({ onRegistered }: AddDeviceFormProps) {
  const [name, setName] = useState('')
  const [externalId, setExternalId] = useState('')
  const [kindIndex, setKindIndex] = useState(0)
  const [sensors, setSensors] = useState<SensorRow[]>([defaultSensor()])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const kind = DEVICE_KINDS[kindIndex]

  function changeSensorType(index: number, type: string) {
    const preset = SENSOR_PRESETS.find((entry) => entry.type === type) ?? SENSOR_PRESETS[0]
    setSensors((current) =>
      current.map((sensor, i) => (i === index ? { ...preset, id: sensor.id } : sensor)),
    )
  }

  function updateSensor(index: number, field: 'key' | 'unit', value: string) {
    setSensors((current) =>
      current.map((sensor, i) => (i === index ? { ...sensor, [field]: value } : sensor)),
    )
  }

  function addSensor() {
    setSensors((current) => [...current, defaultSensor()])
  }

  function removeSensor(index: number) {
    setSensors((current) => current.filter((_, i) => i !== index))
  }

  function buildRegistration(): DeviceRegistration {
    const base = { externalId: externalId.trim(), name: name.trim(), type: kind.type }
    if (kind.sensing) {
      return {
        ...base,
        sensors: sensors.map((sensor) => ({
          key: sensor.key.trim(),
          type: sensor.type,
          unit: sensor.unit.trim(),
        })),
      }
    }
    return { ...base, adapterType: kind.adapterType }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const device = await registerDevice(buildRegistration())
      onRegistered(device)
      setName('')
      setExternalId('')
      setSensors([defaultSensor()])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Something went wrong')
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="add-device" onSubmit={handleSubmit}>
      <h2>Add device</h2>
      {error !== null && (
        <p className="add-device__error" role="alert">
          {error}
        </p>
      )}
      <div className="add-device__fields">
        <label className="add-device__field">
          Name
          <input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Desk Lamp"
            required
            disabled={pending}
          />
        </label>
        <label className="add-device__field">
          {kind.addressLabel}
          <input
            value={externalId}
            onChange={(event) => setExternalId(event.target.value)}
            placeholder={kind.addressPlaceholder}
            required
            disabled={pending}
          />
        </label>
        <label className="add-device__field">
          Kind
          <select
            value={kindIndex}
            onChange={(event) => setKindIndex(Number(event.target.value))}
            disabled={pending}
          >
            {DEVICE_KINDS.map((option, index) => (
              <option key={option.type} value={index}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {kind.sensing && (
        <fieldset className="add-device__sensors" disabled={pending}>
          <legend>Sensors</legend>
          {sensors.map((sensor, index) => (
            <div className="add-device__sensor" key={sensor.id}>
              <label className="add-device__field">
                Sensor type
                <select
                  value={sensor.type}
                  onChange={(event) => changeSensorType(index, event.target.value)}
                >
                  {SENSOR_PRESETS.map((preset) => (
                    <option key={preset.type} value={preset.type}>
                      {preset.type}
                    </option>
                  ))}
                </select>
              </label>
              <label className="add-device__field">
                Key
                <input
                  value={sensor.key}
                  onChange={(event) => updateSensor(index, 'key', event.target.value)}
                  required
                />
              </label>
              <label className="add-device__field">
                Unit
                <input
                  value={sensor.unit}
                  onChange={(event) => updateSensor(index, 'unit', event.target.value)}
                  required
                />
              </label>
              <button
                type="button"
                className="add-device__sensor-remove"
                onClick={() => removeSensor(index)}
                disabled={sensors.length === 1}
                aria-label={`Remove sensor ${index + 1}`}
              >
                &times;
              </button>
            </div>
          ))}
          <button type="button" className="add-device__sensor-add" onClick={addSensor}>
            + Add sensor
          </button>
        </fieldset>
      )}

      <div className="add-device__actions">
        <button type="submit" className="add-device__submit" disabled={pending}>
          Add device
        </button>
      </div>
    </form>
  )
}
