import type { Device } from '../api/devices'
import { AddDeviceForm } from '../components/AddDeviceForm'
import { HuePanel } from '../components/HuePanel'

interface ConfigurationPageProps {
  onRegistered: (device: Device) => void
}

/** The configuration view: register devices by hand and pair bridges that bring in their own. */
export function ConfigurationPage({ onRegistered }: ConfigurationPageProps) {
  return (
    <section className="configuration">
      <AddDeviceForm onRegistered={onRegistered} />
      <HuePanel onRegistered={onRegistered} />
    </section>
  )
}
