import { Building2, MapPin } from 'lucide-react'
import type { MatchResponse } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent } from '@/components/ui/Card'
import { ScoreRing } from '@/components/ui/ScoreRing'
import { formatSalaryRange } from '@/lib/utils'

export function JobCard({ match, onClick }: { match: MatchResponse; onClick: () => void }) {
  const { job } = match
  const salary = formatSalaryRange(job.salaryMin, job.salaryMax, job.salaryCurrency)

  return (
    <Card
      hoverable
      className="cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick()
        }
      }}
    >
      <CardContent className="flex items-start gap-4">
        <ScoreRing score={match.overallScore} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-ink">{job.title}</p>
          <div className="mt-0.5 flex items-center gap-1.5 text-sm text-ink-muted">
            <Building2 className="size-3.5 shrink-0" />
            <span className="truncate">{job.company}</span>
          </div>
          {job.location && (
            <div className="mt-0.5 flex items-center gap-1.5 text-sm text-ink-muted">
              <MapPin className="size-3.5 shrink-0" />
              <span className="truncate">{job.location}</span>
            </div>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {job.remote && <Badge tone="brand">Remote</Badge>}
            {salary && <Badge tone="success">{salary}</Badge>}
            {match.matchedSkills.length > 0 && (
              <Badge tone="neutral">{match.matchedSkills.length} skills matched</Badge>
            )}
          </div>
          {job.sourceAttribution && (
            <p className="mt-2 text-xs text-ink-faint">via {job.sourceAttribution}</p>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
