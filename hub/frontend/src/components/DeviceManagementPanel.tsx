import { useState } from 'react'
import type { Device } from '../api/devices'
import { deleteDevice, renameDevice } from '../api/devices'
import { isOffline } from '../deviceHealth'

interface DeviceManagementPanelProps {
  /** Every registered device, from the shared app state. */
  devices: Device[]
  /** Called with the renamed device so the shared state shows the new name at once. */
  onRenamed: (device: Device) => void
  /** Called with the removed device's id so it drops from the shared state at once. */
  onDeleted: (id: number) => void
}

/**
 * Lists every registered device and lets you rename or delete one. A delete asks for a second click
 * first, since it is destructive; a sensor node that is still publishing reappears on its next
 * reading.
 */
export function DeviceManagementPanel({
  devices,
  onRenamed,
  onDeleted,
}: DeviceManagementPanelProps) {
  const [renamingId, setRenamingId] = useState<number | null>(null)
  const [draftName, setDraftName] = useState('')
  const [confirmingId, setConfirmingId] = useState<number | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  function startRename(device: Device) {
    setError(null)
    setConfirmingId(null)
    setRenamingId(device.id)
    setDraftName(device.name)
  }

  function startDelete(id: number) {
    setError(null)
    setRenamingId(null)
    setConfirmingId(id)
  }

  async function saveRename(device: Device) {
    const name = draftName.trim()
    if (name === '' || name === device.name) {
      setRenamingId(null)
      return
    }
    setBusyId(device.id)
    setError(null)
    try {
      const updated = await renameDevice(device.id, name)
      onRenamed(updated)
      setRenamingId(null)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusyId(null)
    }
  }

  async function confirmDelete(id: number) {
    setBusyId(id)
    setError(null)
    try {
      await deleteDevice(id)
      onDeleted(id)
      setConfirmingId(null)
    } catch (cause) {
      setError(messageOf(cause))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="config-panel device-admin">
      <h2>Manage devices</h2>
      <p className="config-panel__hint">
        Rename a device, or remove one you no longer use. A sensor node that is still publishing
        reappears on its next reading.
      </p>
      {error !== null && (
        <p className="config-panel__error" role="alert">
          {error}
        </p>
      )}
      {devices.length === 0 ? (
        <p className="config-panel__hint">No devices yet.</p>
      ) : (
        <ul className="device-admin__list">
          {devices.map((device) => (
            <li className="device-admin__row" key={device.id}>
              {renamingId === device.id ? (
                <form
                  className="device-admin__rename"
                  onSubmit={(event) => {
                    event.preventDefault()
                    void saveRename(device)
                  }}
                >
                  <input
                    aria-label={`New name for ${device.name}`}
                    value={draftName}
                    onChange={(event) => setDraftName(event.target.value)}
                    disabled={busyId === device.id}
                  />
                  <button type="submit" disabled={busyId === device.id}>
                    Save
                  </button>
                  <button
                    type="button"
                    onClick={() => setRenamingId(null)}
                    disabled={busyId === device.id}
                  >
                    Cancel
                  </button>
                </form>
              ) : (
                <>
                  <div className="device-admin__info">
                    <span className="device-admin__name">{device.name}</span>
                    {isOffline(device) && <span className="device-admin__badge">Offline</span>}
                    <span className="device-admin__meta">{device.externalId}</span>
                  </div>
                  <div className="device-admin__actions">
                    {confirmingId === device.id ? (
                      <>
                        <span className="device-admin__prompt">Delete {device.name}?</span>
                        <button
                          type="button"
                          className="device-admin__delete"
                          onClick={() => void confirmDelete(device.id)}
                          disabled={busyId === device.id}
                        >
                          Confirm
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmingId(null)}
                          disabled={busyId === device.id}
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          onClick={() => startRename(device)}
                          disabled={busyId === device.id}
                        >
                          Rename
                        </button>
                        <button
                          type="button"
                          className="device-admin__delete"
                          onClick={() => startDelete(device.id)}
                          disabled={busyId === device.id}
                        >
                          Delete
                        </button>
                      </>
                    )}
                  </div>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong'
}
