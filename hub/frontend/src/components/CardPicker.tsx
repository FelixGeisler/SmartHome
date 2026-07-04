import type { Device } from '../api/devices'

interface CardPickerProps {
  open: boolean
  /** Registered devices not yet on the dashboard. */
  devices: Device[]
  onClose: () => void
  onAdd: (device: Device) => void
}

/**
 * A modal listing the registered devices not on the dashboard, so a card can be added for one.
 * Registering new devices happens on the Configuration page; this only chooses which existing
 * devices to show. It stays open after a pick so several cards can be added in one sitting.
 */
export function CardPicker({ open, devices, onClose, onAdd }: CardPickerProps) {
  if (!open) {
    return null
  }
  return (
    <div className="card-picker" role="presentation" onClick={onClose}>
      <section
        className="card-picker__panel"
        role="dialog"
        aria-modal="true"
        aria-label="Add a card"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="card-picker__head">
          <h2 className="card-picker__title">Add a card</h2>
          <button type="button" className="card-picker__close" aria-label="Close" onClick={onClose}>
            {'×'}
          </button>
        </header>
        {devices.length === 0 ? (
          <p className="card-picker__empty">
            Every device is already on the dashboard. Register more in Configuration.
          </p>
        ) : (
          <ul className="card-picker__list">
            {devices.map((device) => (
              <li key={device.id}>
                <button type="button" className="card-picker__item" onClick={() => onAdd(device)}>
                  {device.name}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
