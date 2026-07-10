import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Device, DeviceRegistration } from '../api/devices'
import { registerDevice } from '../api/devices'

// Sensor nodes aren't listed here; they auto-provision on their first MQTT reading.
type DeviceKind = {
  label: string
  type: string
  addressLabel: string
  addressPlaceholder: string
  adapterType: string
}

const DEVICE_KINDS: DeviceKind[] = [
  {
    label: 'Shelly Plug',
    type: 'SHELLY_PLUG',
    addressLabel: 'Host',
    addressPlaceholder: '192.168.1.50',
    adapterType: 'shelly',
  },
]

interface AddDeviceFormProps {
  onRegistered: (device: Device) => void
}

export function AddDeviceForm({ onRegistered }: AddDeviceFormProps) {
  const [name, setName] = useState('')
  const [externalId, setExternalId] = useState('')
  const [kindIndex, setKindIndex] = useState(0)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const kind = DEVICE_KINDS[kindIndex]

  function buildRegistration(): DeviceRegistration {
    return {
      externalId: externalId.trim(),
      name: name.trim(),
      type: kind.type,
      adapterType: kind.adapterType,
    }
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
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Something went wrong')
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="config-panel add-device" onSubmit={handleSubmit}>
      <h2>Add device</h2>
      <p className="config-panel__hint">
        Add a device the hub cannot discover on its own by its address. MQTT sensor nodes appear
        automatically on their first reading, so there is nothing to add here for them.
      </p>
      {error !== null && (
        <p className="config-panel__error" role="alert">
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
        {DEVICE_KINDS.length > 1 && (
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
        )}
      </div>

      <div className="add-device__actions">
        <button type="submit" className="add-device__submit" disabled={pending}>
          Add device
        </button>
      </div>
    </form>
  )
}
