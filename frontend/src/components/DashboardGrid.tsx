import { useState, useCallback } from 'react'
import GridLayout, { type LayoutItem } from 'react-grid-layout'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'
import { Pencil, Save, Plus, X, Trash2 } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api } from '@/api/client'
import type { DashboardConfig, WidgetDef } from '@/api/client'
import { cn } from '@/lib/utils'
import { WidgetPicker } from './WidgetPicker'
import { WIDGET_REGISTRY } from './widgets/registry'

interface Props {
  dashboard: DashboardConfig
}

// WidgetRenderer must return a SINGLE root div (not a Fragment) so that
// react-resizable can inject the resize handle as its direct sibling inside
// the .react-grid-item wrapper — keeping the handle as a direct child so
// the CSS child-combinator rules in styles.css apply correctly.
function WidgetRenderer({ widget, editMode, onRemove }: {
  widget: WidgetDef
  editMode: boolean
  onRemove: () => void
}) {
  const entry = WIDGET_REGISTRY[widget.type as keyof typeof WIDGET_REGISTRY]
  const content = entry
    ? <entry.Component config={widget.config} />
    : <div className="p-4 text-sm text-[hsl(var(--muted-foreground))]">Unknown widget</div>

  // Single root div — fills the .react-grid-item (which gets position:absolute +
  // explicit px width/height injected by react-grid-layout via cloneElement).
  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>

      {/* Card shell */}
      <div
        style={{
          position: 'absolute',
          top: 0, right: 0, bottom: 0, left: 0,
          borderRadius: '0.75rem',
          overflow: 'hidden',
          border: editMode
            ? '1px solid hsl(var(--primary) / 0.5)'
            : '1px solid hsl(var(--border))',
          background: 'hsl(var(--card))',
          boxShadow: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
          opacity: editMode ? 0.25 : 1,
          pointerEvents: editMode ? 'none' : 'auto',
          userSelect: editMode ? 'none' : 'auto',
        }}
      >
        {content}
      </div>

      {/* Edit-mode overlay: large centered Remove button */}
      {editMode && (
        <div
          style={{
            position: 'absolute',
            top: 0, right: 0, bottom: 0, left: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'grab',
            borderRadius: '0.75rem',
          }}
        >
          <button
            onMouseDown={e => e.stopPropagation()}
            onClick={onRemove}
            className={cn(
              'flex flex-col items-center gap-1.5 px-6 py-4 rounded-2xl shadow-xl transition-all',
              'bg-destructive text-destructive-foreground hover:opacity-90 active:scale-95',
            )}
          >
            <Trash2 size={24} />
            <span className="text-xs font-semibold tracking-wide uppercase">Remove</span>
          </button>
        </div>
      )}
    </div>
  )
}

export function DashboardGrid({ dashboard }: Props) {
  const [editMode, setEditMode] = useState(false)
  const [showPicker, setShowPicker] = useState(false)
  const queryClient = useQueryClient()

  const layouts: Record<string, LayoutItem[]> = (() => {
    try { return JSON.parse(dashboard.layoutJson || '{}') } catch { return {} }
  })()
  const widgets: WidgetDef[] = (() => {
    try { return JSON.parse(dashboard.widgetsJson || '[]') } catch { return [] }
  })()

  const [localLayouts, setLocalLayouts] = useState<Record<string, LayoutItem[]>>(layouts)
  const [localWidgets, setLocalWidgets] = useState<WidgetDef[]>(widgets)

  const saveMutation = useMutation({
    mutationFn: () => api.dashboards.update(dashboard.id, {
      layoutJson: JSON.stringify(localLayouts),
      widgetsJson: JSON.stringify(localWidgets),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dashboard', dashboard.id] })
      setEditMode(false)
      toast.success('Dashboard saved')
    },
    onError: () => toast.error('Failed to save dashboard'),
  })

  const onLayoutChange = useCallback((newLayout: readonly LayoutItem[]) => {
    setLocalLayouts(prev => ({ ...prev, lg: [...newLayout] }))
  }, [])

  function addWidget(widget: WidgetDef) {
    const id = widget.id
    const newItem: LayoutItem = { i: id, x: 0, y: Infinity, w: 4, h: 4 }
    setLocalLayouts(prev => {
      const updated: Record<string, LayoutItem[]> = {}
      for (const bp of ['lg', 'md', 'sm']) {
        updated[bp] = [...(prev[bp] ?? []), newItem]
      }
      return updated
    })
    setLocalWidgets(prev => [...prev, widget])
    setShowPicker(false)
  }

  function removeWidget(id: string) {
    setLocalWidgets(prev => prev.filter(w => w.id !== id))
    setLocalLayouts(prev => {
      const updated: Record<string, LayoutItem[]> = {}
      for (const bp of Object.keys(prev)) {
        updated[bp] = (prev[bp] ?? []).filter(l => l.i !== id)
      }
      return updated
    })
  }

  const currentLayout = localLayouts['lg'] ?? []

  return (
    <div className={cn('h-full', editMode && 'edit-mode')}>
      {/* Toolbar */}
      <div className="flex items-center justify-between px-6 py-3 border-b border-[hsl(var(--border))] bg-[hsl(var(--card))]">
        <h1 className="font-semibold text-lg">{dashboard.name}</h1>
        <div className="flex items-center gap-2">
          {editMode ? (
            <>
              <button
                onClick={() => setShowPicker(true)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm bg-[hsl(var(--secondary))] hover:bg-[hsl(var(--accent))] transition-colors"
              >
                <Plus size={14} /> Add Widget
              </button>
              <button
                onClick={() => { setEditMode(false); setLocalLayouts(layouts); setLocalWidgets(widgets) }}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] transition-colors"
              >
                <X size={14} /> Cancel
              </button>
              <button
                onClick={() => saveMutation.mutate()}
                disabled={saveMutation.isPending}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] hover:opacity-90 transition-opacity"
              >
                <Save size={14} /> {saveMutation.isPending ? 'Saving…' : 'Save'}
              </button>
            </>
          ) : (
            <button
              onClick={() => setEditMode(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] transition-colors"
            >
              <Pencil size={14} /> Edit
            </button>
          )}
        </div>
      </div>

      {/* Grid */}
      <div className="p-4 overflow-auto">
        {localWidgets.length === 0 && !editMode && (
          <div className="flex flex-col items-center justify-center h-64 text-[hsl(var(--muted-foreground))]">
            <p className="text-lg mb-2">No widgets yet</p>
            <button onClick={() => setEditMode(true)} className="text-sm underline">
              Click Edit to add your first widget
            </button>
          </div>
        )}
        <GridLayout
          className="layout"
          layout={currentLayout}
          gridConfig={{ cols: 12, rowHeight: 80, margin: [12, 12] as [number, number] }}
          dragConfig={{ enabled: editMode, cancel: 'button' }}
          resizeConfig={{ enabled: editMode }}
          width={1200}
          onLayoutChange={onLayoutChange}
        >
          {localWidgets.map(widget => (
            <div key={widget.id}>
              <WidgetRenderer
                widget={widget}
                editMode={editMode}
                onRemove={() => removeWidget(widget.id)}
              />
            </div>
          ))}
        </GridLayout>
      </div>

      {showPicker && (
        <WidgetPicker onAdd={addWidget} onClose={() => setShowPicker(false)} />
      )}
    </div>
  )
}
