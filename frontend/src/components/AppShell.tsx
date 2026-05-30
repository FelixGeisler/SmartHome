import { NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Moon, Sun, Wifi, WifiOff,
  LayoutDashboard, Settings, Cpu, Zap, Plus,
  DoorOpen, Trash2, Layers, AlertTriangle,
} from 'lucide-react'
import { api } from '@/api/client'
import { useLiveStore } from '@/store/liveStore'
import { cn } from '@/lib/utils'
import { toast } from 'sonner'
import { ChatPanel } from '@/components/ChatPanel'

const navItemClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
    isActive
      ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
      : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] hover:text-[hsl(var(--foreground))]'
  )

export function AppShell({ children }: { children: React.ReactNode }) {
  const { darkMode, toggleDarkMode, wsConnected } = useLiveStore()
  const navigate  = useNavigate()
  const location  = useLocation()

  const { data: dashboards = [], refetch } = useQuery({
    queryKey: ['dashboards'],
    queryFn: api.dashboards.list,
  })

  // Offline device count for the health badge on the Devices link
  const { data: devices = [] } = useQuery({
    queryKey: ['devices'],
    queryFn: api.devices.list,
    refetchInterval: 60_000,
  })
  const offlineCount = devices.filter(d => !d.online).length

  async function createDashboard() {
    const name = prompt('Dashboard name:')
    if (!name?.trim()) return
    try {
      const created = await api.dashboards.create({ name: name.trim() })
      await refetch()
      navigate(`/dashboard/${created.id}`)
    } catch {
      toast.error('Failed to create dashboard')
    }
  }

  async function deleteDashboard(id: number, e: React.MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    if (!confirm('Delete this dashboard?')) return
    try {
      await api.dashboards.delete(id)
      const result = await refetch()
      const remaining = result.data ?? []
      if (location.pathname === `/dashboard/${id}`) {
        if (remaining.length > 0) navigate(`/dashboard/${remaining[0].id}`)
        else navigate('/')
      }
      toast.success('Dashboard deleted')
    } catch {
      toast.error('Failed to delete dashboard')
    }
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* ── Sidebar ─────────────────────────────────────────────── */}
      <aside className="w-56 shrink-0 flex flex-col border-r border-[hsl(var(--border))] bg-[hsl(var(--card))]">
        {/* Logo */}
        <div className="flex items-center gap-2 px-4 py-4 border-b border-[hsl(var(--border))]">
          <span className="text-xl">🏠</span>
          <span className="font-semibold text-sm">SmartHome Hub</span>
        </div>

        <nav className="flex-1 overflow-y-auto p-3 space-y-1">
          {/* ── Dashboards ─────────────────────────────────────── */}
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] px-2 mt-2 mb-1">
            Dashboards
          </p>
          {dashboards.map(d => (
            <div key={d.id} className="group relative flex items-center">
              <NavLink to={`/dashboard/${d.id}`} className={({ isActive }) => cn(
                'flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors flex-1 min-w-0 pr-7',
                isActive
                  ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                  : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] hover:text-[hsl(var(--foreground))]'
              )}>
                <LayoutDashboard size={15} className="shrink-0" />
                <span className="truncate">{d.name}</span>
              </NavLink>
              <button
                onClick={e => deleteDashboard(d.id, e)}
                className="absolute right-1 p-1 rounded opacity-0 group-hover:opacity-100 text-[hsl(var(--muted-foreground))] hover:text-red-400 hover:bg-[hsl(var(--accent))] transition-all"
                title="Delete dashboard"
              >
                <Trash2 size={11} />
              </button>
            </div>
          ))}
          <button
            onClick={createDashboard}
            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] hover:text-[hsl(var(--foreground))] transition-colors"
          >
            <Plus size={15} />
            New Dashboard
          </button>

          {/* ── Home ───────────────────────────────────────────── */}
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] px-2 mt-4 mb-1">
            Home
          </p>
          {/* Rooms IS the floor plan — labels it accordingly */}
          <NavLink to="/rooms" className={navItemClass}>
            <DoorOpen size={15} /> Rooms
          </NavLink>
          <NavLink to="/scenes" className={navItemClass}>
            <Layers size={15} /> Scenes
          </NavLink>

          {/* ── Settings ───────────────────────────────────────── */}
          <p className="text-[10px] font-semibold uppercase tracking-widest text-[hsl(var(--muted-foreground))] px-2 mt-4 mb-1">
            Settings
          </p>
          <NavLink to="/settings/devices" className={navItemClass}>
            <Cpu size={15} />
            <span className="flex-1">Devices</span>
            {offlineCount > 0 && (
              <span
                title={`${offlineCount} device(s) offline`}
                className="flex items-center gap-0.5 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-red-500/15 text-red-400"
              >
                <AlertTriangle size={9} />
                {offlineCount}
              </span>
            )}
          </NavLink>
          <NavLink to="/settings/integrations" className={navItemClass}>
            <Settings size={15} /> Integrations
          </NavLink>
          {/* Automations page has Rules + History tabs built in */}
          <NavLink to="/settings/automations" className={navItemClass}>
            <Zap size={15} /> Automations
          </NavLink>
        </nav>

        {/* Bottom bar */}
        <div className="p-3 border-t border-[hsl(var(--border))] flex items-center justify-between">
          <span className={cn('flex items-center gap-1.5 text-xs', wsConnected ? 'text-green-500' : 'text-red-400')}>
            {wsConnected ? <Wifi size={13} /> : <WifiOff size={13} />}
            {wsConnected ? 'Live' : 'Offline'}
          </span>
          <button
            onClick={toggleDarkMode}
            className="p-1.5 rounded-md hover:bg-[hsl(var(--accent))] transition-colors"
            title="Toggle dark mode"
          >
            {darkMode ? <Sun size={15} /> : <Moon size={15} />}
          </button>
        </div>
      </aside>

      {/* ── Main content ─────────────────────────────────────────── */}
      <main className="flex-1 overflow-y-auto bg-[hsl(var(--background))]">
        {children}
      </main>

      {/* ── Ask-the-home chat (Anthropic + MCP tools) ──────────── */}
      <ChatPanel />
    </div>
  )
}
