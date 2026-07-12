import { useState } from 'react'
import type { Device } from '../api/devices'
import { AddDeviceForm } from '../components/AddDeviceForm'
import { AssistantPanel } from '../components/AssistantPanel'
import { DeviceManagementPanel } from '../components/DeviceManagementPanel'
import { HomematicPanel } from '../components/HomematicPanel'
import { HuePanel } from '../components/HuePanel'
import { MqttPanel } from '../components/MqttPanel'
import { SolakonIrPanel } from '../components/SolakonIrPanel'
import { SolakonPanel } from '../components/SolakonPanel'

interface ConfigurationPageProps {
  devices: Device[]
  onRegistered: (device: Device) => void
  onDeviceUpdated: (device: Device) => void
  onDeviceDeleted: (id: number) => void
}

type TabId = 'devices' | 'mqtt' | 'solakon' | 'solakonIr' | 'hue' | 'homematic' | 'assistant'

const TABS: ReadonlyArray<{ id: TabId; label: string }> = [
  { id: 'devices', label: 'Devices' },
  { id: 'mqtt', label: 'MQTT' },
  { id: 'solakon', label: 'Solakon' },
  { id: 'solakonIr', label: 'Grid meter' },
  { id: 'hue', label: 'Hue' },
  { id: 'homematic', label: 'Homematic' },
  { id: 'assistant', label: 'Assistant' },
]

/** The configuration view: register devices by hand and pair bridges that bring in their own. */
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
            aria-controls="config-tabpanel"
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
        id="config-tabpanel"
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
        {active === 'solakonIr' && <SolakonIrPanel />}
        {active === 'hue' && <HuePanel onRegistered={onRegistered} />}
        {active === 'homematic' && <HomematicPanel onRegistered={onRegistered} />}
        {active === 'assistant' && <AssistantPanel />}
      </div>
    </section>
  )
}
