import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppShell } from '@/components/AppShell'
import { DashboardPage } from '@/pages/DashboardPage'
import { RoomsPage } from '@/pages/RoomsPage'
import { DevicesPage } from '@/pages/settings/DevicesPage'
import { IntegrationsPage } from '@/pages/settings/IntegrationsPage'
import { AutomationsPage } from '@/pages/settings/AutomationsPage'
import { ScenesPage } from '@/pages/settings/ScenesPage'
import { useWebSocket } from '@/hooks/useWebSocket'

function AppWithWebSocket() {
  useWebSocket()
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard/1" replace />} />
        <Route path="/dashboard/:id" element={<DashboardPage />} />
        <Route path="/rooms" element={<RoomsPage />} />
        <Route path="/scenes" element={<ScenesPage />} />
        <Route path="/settings/devices" element={<DevicesPage />} />
        <Route path="/settings/integrations" element={<IntegrationsPage />} />
        <Route path="/settings/automations" element={<AutomationsPage />} />
        {/* Legacy redirect so old bookmarks still work */}
        <Route path="/settings/automation-history" element={<Navigate to="/settings/automations" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AppShell>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppWithWebSocket />
    </BrowserRouter>
  )
}
