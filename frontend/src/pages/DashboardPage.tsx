import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { api } from '@/api/client'
import { DashboardGrid } from '@/components/DashboardGrid'

export function DashboardPage() {
  const { id } = useParams<{ id: string }>()
  const dashboardId = Number(id)
  const navigate = useNavigate()

  const { data: dashboard, isLoading, isError } = useQuery({
    queryKey: ['dashboard', dashboardId],
    queryFn: () => api.dashboards.get(dashboardId),
    enabled: !!dashboardId,
    retry: false,
  })

  const { data: allDashboards = [] } = useQuery({
    queryKey: ['dashboards'],
    queryFn: api.dashboards.list,
    enabled: isError,
  })

  useEffect(() => {
    if (isError && allDashboards.length > 0) {
      const first = allDashboards[0]
      if (first.id !== dashboardId) navigate(`/dashboard/${first.id}`, { replace: true })
    }
  }, [isError, allDashboards, dashboardId, navigate])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full text-[hsl(var(--muted-foreground))] text-sm">
        Loading…
      </div>
    )
  }

  if (isError || !dashboard) {
    return (
      <div className="flex items-center justify-center h-full text-[hsl(var(--muted-foreground))] text-sm">
        {allDashboards.length === 0 ? 'No dashboards yet — use the sidebar to create one.' : 'Redirecting…'}
      </div>
    )
  }

  return <DashboardGrid dashboard={dashboard} />
}
