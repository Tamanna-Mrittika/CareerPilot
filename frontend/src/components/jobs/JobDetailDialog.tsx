import { useMutation } from '@tanstack/react-query'
import { Building2, ExternalLink, MapPin, Plus } from 'lucide-react'
import type { ComponentScore, MatchResponse } from '@/lib/api/types'
import { trackerApi } from '@/lib/api/tracker'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog'
import { ScoreRing } from '@/components/ui/ScoreRing'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { cn, formatSalaryRange, scoreBand, titleCase } from '@/lib/utils'

const componentLabels: Record<string, string> = {
  skills: 'Skills',
  experience: 'Experience',
  location: 'Location',
  workStyle: 'Work style',
}

const bandBarClass = { low: 'bg-score-low', mid: 'bg-score-mid', high: 'bg-score-high' } as const

function ComponentRow({ component }: { component: ComponentScore }) {
  const applicable = component.score >= 0
  const label = componentLabels[component.component] ?? titleCase(component.component)

  return (
    <div>
      <div className="mb-1 flex items-center justify-between text-sm">
        <span className="font-medium text-ink">
          {label} <span className="text-ink-faint">({Math.round(component.weight * 100)}%)</span>
        </span>
        <span className="font-semibold text-ink">{applicable ? `${Math.round(component.score)}%` : 'N/A'}</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-sunken">
        {applicable && (
          <div
            className={cn('h-full rounded-full transition-all', bandBarClass[scoreBand(component.score)])}
            style={{ width: `${component.score}%` }}
          />
        )}
      </div>
      <p className="mt-1 text-xs text-ink-muted">{component.explanation}</p>
    </div>
  )
}

export function JobDetailDialog({
  match,
  open,
  onOpenChange,
}: {
  match: MatchResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const addToTracker = useMutation({
    mutationFn: () => {
      if (!match) throw new Error('No job selected')
      return trackerApi.create({
        jobId: match.job.id,
        jobTitle: match.job.title,
        company: match.job.company,
        applyUrl: match.job.applyUrl,
        location: match.job.location,
      })
    },
    onSuccess: () => {
      toast({ title: 'Added to your tracker', description: 'Saved to Wishlist', variant: 'success' })
    },
    onError: (error) => toastFromError(error, 'Could not add to tracker'),
  })

  if (!match) return null
  const { job } = match
  const salary = formatSalaryRange(job.salaryMin, job.salaryMax, job.salaryCurrency)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <div className="flex items-start gap-4">
            <ScoreRing score={match.overallScore} size="lg" />
            <div>
              <DialogTitle>{job.title}</DialogTitle>
              <div className="mt-1 flex items-center gap-1.5 text-sm text-ink-muted">
                <Building2 className="size-3.5" /> {job.company}
              </div>
              {job.location && (
                <div className="mt-0.5 flex items-center gap-1.5 text-sm text-ink-muted">
                  <MapPin className="size-3.5" /> {job.location}
                </div>
              )}
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {job.remote && <Badge tone="brand">Remote</Badge>}
            {salary && <Badge tone="success">{salary}</Badge>}
          </div>
        </DialogHeader>

        <div className="space-y-4">
          {match.breakdown.map((c) => (
            <ComponentRow key={c.component} component={c} />
          ))}
        </div>

        {match.matchedSkills.length > 0 && (
          <div className="mt-5">
            <p className="mb-1.5 text-sm font-semibold text-ink">Skills you have</p>
            <div className="flex flex-wrap gap-1.5">
              {match.matchedSkills.map((s) => (
                <Badge key={s.slug} tone="success">
                  {s.name}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {match.missingSkills.length > 0 && (
          <div className="mt-4">
            <p className="mb-1.5 text-sm font-semibold text-ink">Skills to develop</p>
            <div className="space-y-2">
              {match.missingSkills.map((s) => (
                <div key={s.slug} className="rounded-control border border-border p-2.5">
                  <p className="text-sm font-medium text-ink">{s.name}</p>
                  {s.courses.length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
                      {s.courses.map((c) => (
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
              ))}
            </div>
          </div>
        )}

        <DialogFooter className="justify-between sm:justify-between">
          {job.applyUrl && (
            <a
              href={job.applyUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-sm font-medium text-brand-600 hover:text-brand-700"
            >
              View original posting <ExternalLink className="size-3.5" />
            </a>
          )}
          <Button onClick={() => addToTracker.mutate()} loading={addToTracker.isPending}>
            <Plus className="size-4" />
            Add to tracker
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
