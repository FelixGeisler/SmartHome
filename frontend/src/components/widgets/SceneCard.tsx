import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { Play } from 'lucide-react'
import { api } from '@/api/client'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'

interface Props { config: Record<string, unknown> }

export function SceneCard({ config }: Props) {
  const sceneId = Number(config.sceneId ?? 0)
  const [firing, setFiring] = useState(false)

  const { data: scenes = [] } = useQuery({
    queryKey: ['scenes'],
    queryFn: api.scenes.list,
  })

  const scene = scenes.find(s => s.id === sceneId)

  const activate = useMutation({
    mutationFn: () => api.scenes.activate(sceneId),
    onMutate: () => setFiring(true),
    onSuccess: () => {
      toast.success(`Scene "${scene?.name ?? sceneId}" activated`)
      setTimeout(() => setFiring(false), 600)
    },
    onError: () => {
      toast.error('Failed to activate scene')
      setFiring(false)
    },
  })

  if (!sceneId) {
    return (
      <div className="flex h-full items-center justify-center text-xs text-[hsl(var(--muted-foreground))]">
        No scene configured
      </div>
    )
  }

  return (
    <button
      type="button"
      disabled={activate.isPending}
      onClick={() => activate.mutate()}
      className={cn(
        'flex h-full w-full flex-col items-center justify-center gap-3 rounded-xl transition-all select-none',
        'active:scale-95',
        firing
          ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
          : 'hover:bg-[hsl(var(--accent))] text-[hsl(var(--foreground))]'
      )}
    >
      <span className="text-4xl leading-none">{scene?.icon ?? '🎬'}</span>
      <span className="text-sm font-semibold">{scene?.name ?? `Scene ${sceneId}`}</span>
      <span className={cn(
        'flex items-center gap-1 text-xs transition-colors',
        firing ? 'text-[hsl(var(--primary-foreground))]' : 'text-[hsl(var(--muted-foreground))]'
      )}>
        <Play size={11} />
        {firing ? 'Activating…' : 'Tap to activate'}
      </span>
    </button>
  )
}
