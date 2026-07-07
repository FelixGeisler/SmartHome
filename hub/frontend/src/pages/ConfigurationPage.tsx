import type { Device } from '../api/devices'
import { AddDeviceForm } from '../components/AddDeviceForm'
import { AssistantPanel } from '../components/AssistantPanel'
import { DeviceManagementPanel } from '../components/DeviceManagementPanel'
import { HomematicPanel } from '../components/HomematicPanel'
import { HuePanel } from '../components/HuePanel'
import { MqttPanel } from '../components/MqttPanel'
import { SolakonPanel } from '../components/SolakonPanel'

interface ConfigurationPageProps {
  /** Every registered device, for the manage-devices panel. */
  devices: Device[]
  onRegistered: (device: Device) => void
  /** Called with a renamed device so the shared state reflects it at once. */
  onDeviceUpdated: (device: Device) => void
  /** Called with a deleted device's id so it drops from the shared state at once. */
  onDeviceDeleted: (id: number) => void
}

/** The configuration view: register devices by hand and pair bridges that bring in their own. */
export function ConfigurationPage({
  devices,
  onRegistered,
  onDeviceUpdated,
  onDeviceDeleted,
}: ConfigurationPageProps) {
  return (
    <section className="configuration">
      <AddDeviceForm onRegistered={onRegistered} />
      <MqttPanel />
      <SolakonPanel />
      <HuePanel onRegistered={onRegistered} />
      <HomematicPanel onRegistered={onRegistered} />
      <AssistantPanel />
      <DeviceManagementPanel
        devices={devices}
        onRenamed={onDeviceUpdated}
        onDeleted={onDeviceDeleted}
      />
    </section>
  )
}
