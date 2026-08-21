import { useQuery } from '@tanstack/react-query'
import { Activity, CircleCheck, CircleOff, CircleSlash } from 'lucide-react'
import { jobsApi } from '@/lib/api/jobs'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { PageSpinner } from '@/components/ui/Spinner'
import { cn } from '@/lib/utils'

const breakerIcon = {
  CLOSED: CircleCheck,
  OPEN: CircleOff,
  HALF_OPEN: CircleSlash,
} as const

const breakerTone = {
  CLOSED: 'success',
  OPEN: 'danger',
  HALF_OPEN: 'warning',
} as const

export function SystemPage() {
  const statsQuery = useQuery({
    queryKey: ['jobs', 'stats'],
    queryFn: jobsApi.stats,
    refetchInterval: 10000,
  })

  if (statsQuery.isLoading || !statsQuery.data) return <PageSpinner />
  const stats = statsQuery.data

  return (
    <div className="space-y-6">
      <div>
        <div className="flex items-center gap-2">
          <Activity className="size-5 text-brand-600" />
          <h2 className="text-xl font-semibold text-ink">Job ingestion infrastructure</h2>
        </div>
        <p className="mt-1 text-sm text-ink-muted">
          Live from job-service: every external board runs behind its own Resilience4j circuit breaker, so one
          provider failing never blocks the others. Refreshes every 10 seconds.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card>
          <CardContent>
            <p className="text-sm text-ink-muted">Total listings ingested</p>
            <p className="mt-1 text-3xl font-semibold text-ink">{stats.totalListings.toLocaleString()}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent>
            <p className="text-sm text-ink-muted">Distinct vacancies after dedup</p>
            <p className="mt-1 text-3xl font-semibold text-ink">{stats.distinctVacancies.toLocaleString()}</p>
            <p className="mt-1 text-xs text-ink-faint">
              {stats.totalListings - stats.distinctVacancies} cross-board duplicates collapsed
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Circuit breaker state per provider</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Object.entries(stats.circuitBreakers).map(([provider, state]) => {
              const Icon = breakerIcon[state as keyof typeof breakerIcon] ?? CircleCheck
              const tone = breakerTone[state as keyof typeof breakerTone] ?? 'success'
              return (
                <div key={provider} className="flex items-center justify-between rounded-control border border-border p-3">
                  <span className="text-sm font-medium text-ink">{provider}</span>
                  <Badge tone={tone}>
                    <Icon className="size-3" />
                    {state}
                  </Badge>
                </div>
              )
            })}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Postings by source</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2.5">
            {Object.entries(stats.countsBySource)
              .sort(([, a], [, b]) => b - a)
              .map(([source, count]) => {
                const max = Math.max(...Object.values(stats.countsBySource), 1)
                return (
                  <div key={source}>
                    <div className="mb-1 flex items-center justify-between text-sm">
                      <span className="font-medium text-ink">{source}</span>
                      <span className="text-ink-muted">{count.toLocaleString()}</span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken">
                      <div
                        className={cn('h-full rounded-full bg-brand-600')}
                        style={{ width: `${(count / max) * 100}%` }}
                      />
                    </div>
                  </div>
                )
              })}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
