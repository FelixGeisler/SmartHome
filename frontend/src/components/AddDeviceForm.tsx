import { useState } from 'react'
import type { FormEvent } from 'react'
import type { Device } from '../api/devices'
import { registerDevice } from '../api/devices'

// Each kind pairs the API's device type with the adapter that handles it,
// so the form stays a single dropdown instead of two free-text fields.
const DEVICE_KINDS = [{ label: 'Shelly Plug', type: 'SHELLY_PLUG', adapterType: 'shelly' }]

interface AddDeviceFormProps {
  onRegistered: (device: Device) => void
}

/** Registration form: device name, host, and kind; reports the new device upward. */
export function AddDeviceForm({ onRegistered }: AddDeviceFormProps) {
  const [name, setName] = useState('')
  const [externalId, setExternalId] = useState('')
  const [kindIndex, setKindIndex] = useState(0)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const kind = DEVICE_KINDS[kindIndex]
    setPending(true)
    setError(null)
    try {
      const device = await registerDevice({
        externalId: externalId.trim(),
        name: name.trim(),
        type: kind.type,
        adapterType: kind.adapterType,
      })
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
          Host
          <input
            value={externalId}
            onChange={(event) => setExternalId(event.target.value)}
            placeholder="192.168.1.50"
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
            {DEVICE_KINDS.map((kind, index) => (
              <option key={kind.type} value={index}>
                {kind.label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="add-device__submit" disabled={pending}>
          Add device
        </button>
      </div>
    </form>
  )
}
