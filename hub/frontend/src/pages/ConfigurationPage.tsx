import { useState } from 'react'
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

/** The Configuration sections, one tab each; devices groups the add and manage panels. */
type TabId = 'devices' | 'mqtt' | 'solakon' | 'hue' | 'homematic' | 'assistant'

const TABS: ReadonlyArray<{ id: TabId; label: string }> = [
  { id: 'devices', label: 'Devices' },
  { id: 'mqtt', label: 'MQTT' },
  { id: 'solakon', label: 'Solakon' },
  { id: 'hue', label: 'Hue' },
  { id: 'homematic', label: 'Homematic' },
  { id: 'assistant', label: 'Assistant' },
]

/**
 * The configuration view: register devices by hand and pair bridges that bring in their own. The
 * panels are split across tabs so each integration is a small, focused section rather than one long
 * scroll.
 */
export function ConfigurationPage({
  devices,
  onRegistered,
  onDeviceUpdated,
  onDeviceDeleted,
}: ConfigurationPageProps) {
  const [active, setActive] = useState<TabId>('devices')

  return (
    <section className="configuration">
      <header className="configuration__intro">
        <h2>Configuration</h2>
      </header>

      <div className="configuration__tabs" role="tablist" aria-label="Configuration sections">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`config-tab-${tab.id}`}
            aria-selected={active === tab.id}
            aria-controls={`config-tabpanel-${tab.id}`}
            className={
              active === tab.id
                ? 'configuration__tab configuration__tab--active'
                : 'configuration__tab'
            }
            onClick={() => setActive(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div
        className="configuration__panels"
        role="tabpanel"
        id={`config-tabpanel-${active}`}
        aria-labelledby={`config-tab-${active}`}
      >
        {active === 'devices' && (
          <>
            <AddDeviceForm onRegistered={onRegistered} />
            <DeviceManagementPanel
              devices={devices}
              onRenamed={onDeviceUpdated}
              onDeleted={onDeviceDeleted}
            />
          </>
        )}
        {active === 'mqtt' && <MqttPanel />}
        {active === 'solakon' && <SolakonPanel />}
        {active === 'hue' && <HuePanel onRegistered={onRegistered} />}
        {active === 'homematic' && <HomematicPanel onRegistered={onRegistered} />}
        {active === 'assistant' && <AssistantPanel />}
      </div>
    </section>
  )
}
