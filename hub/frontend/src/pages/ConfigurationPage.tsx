import type { Device } from '../api/devices'
import { AddDeviceForm } from '../components/AddDeviceForm'
import { AssistantPanel } from '../components/AssistantPanel'
import { HuePanel } from '../components/HuePanel'
import { MqttPanel } from '../components/MqttPanel'
import { SolakonPanel } from '../components/SolakonPanel'

interface ConfigurationPageProps {
  onRegistered: (device: Device) => void
}

/** The configuration view: register devices by hand and pair bridges that bring in their own. */
export function ConfigurationPage({ onRegistered }: ConfigurationPageProps) {
  return (
    <section className="configuration">
      <AddDeviceForm onRegistered={onRegistered} />
      <MqttPanel />
      <SolakonPanel />
      <HuePanel onRegistered={onRegistered} />
      <AssistantPanel />
    </section>
  )
}
