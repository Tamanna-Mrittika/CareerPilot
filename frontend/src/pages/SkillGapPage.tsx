import { useQuery } from '@tanstack/react-query'
import { ExternalLink, TrendingUp } from 'lucide-react'
import { useState } from 'react'
import { skillGapApi } from '@/lib/api/matches'
import type { JobScope } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { Input } from '@/components/ui/Input'
import { Skeleton } from '@/components/ui/Skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/Tabs'
import { cn } from '@/lib/utils'

const SCOPES: { value: JobScope; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'LOCAL', label: 'Local' },
  { value: 'REMOTE', label: 'Remote' },
]

export function SkillGapPage() {
  const [scope, setScope] = useState<JobScope>('ALL')
  const [city, setCity] = useState('')

  const query = useQuery({
    queryKey: ['skill-gap', scope, city],
    queryFn: () => skillGapApi.get({ scope, city: city || undefined }),
  })

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-xl font-semibold text-ink">What should you learn next?</h2>
        <p className="mt-1 text-sm text-ink-muted">
          Ranked by how many current postings ask for each skill, not by how rare it is -- the most in-demand gap
          beats an exotic one you don't currently need.
        </p>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <Tabs value={scope} onValueChange={(v) => setScope(v as JobScope)}>
          <TabsList>
            {SCOPES.map((s) => (
              <TabsTrigger key={s.value} value={s.value}>
                {s.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        {scope !== 'REMOTE' && (
          <Input placeholder="City (default: Dhaka)" className="sm:w-52" value={city} onChange={(e) => setCity(e.target.value)} />
        )}
      </div>

      {query.isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : !query.data || query.data.gaps.length === 0 ? (
        <EmptyState icon={TrendingUp} title="No gaps to show" description="We couldn't analyse enough postings for this scope yet." />
      ) : (
        <>
          <p className="text-sm text-ink-muted">Based on {query.data.jobsAnalysed} analysed postings</p>
          <div className="space-y-2.5">
            {query.data.gaps.map((gap, i) => (
              <Card key={gap.slug}>
                <CardContent className="flex items-center gap-4">
                  <span className="w-6 shrink-0 text-center text-sm font-semibold text-ink-faint">{i + 1}</span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-medium text-ink">{gap.name}</p>
                      <Badge tone="brand">{Math.round(gap.demandPercentage)}% of postings</Badge>
                    </div>
                    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken">
                      <div
                        className={cn('h-full rounded-full bg-brand-600 transition-all')}
                        style={{ width: `${Math.min(100, gap.demandPercentage)}%` }}
                      />
                    </div>
                    {gap.courses.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1">
                        {gap.courses.map((c) => (
                          <a
                            key={c.url}
                            href={c.url}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
                          >
                            {c.provider}: {c.title}
                            <ExternalLink className="size-3" />
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
