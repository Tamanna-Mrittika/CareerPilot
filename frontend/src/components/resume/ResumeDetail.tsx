import { useQuery } from '@tanstack/react-query'
import { AlertCircle, AlertTriangle, Info } from 'lucide-react'
import { resumesApi } from '@/lib/api/resumes'
import type { Severity } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { PageSpinner } from '@/components/ui/Spinner'
import { cn, titleCase } from '@/lib/utils'
import { JobScoreCard } from './JobScoreCard'

const severityIcon: Record<Severity, typeof Info> = {
  INFO: Info,
  WARNING: AlertTriangle,
  CRITICAL: AlertCircle,
}

const severityTone: Record<Severity, 'neutral' | 'warning' | 'danger'> = {
  INFO: 'neutral',
  WARNING: 'warning',
  CRITICAL: 'danger',
}

// Tailwind's scanner needs full literal class strings, not `text-${var}` interpolation.
const severityIconClass: Record<Severity, string> = {
  INFO: 'text-ink-muted',
  WARNING: 'text-warning',
  CRITICAL: 'text-danger',
}

export function ResumeDetail({ resumeId }: { resumeId: string }) {
  const query = useQuery({
    queryKey: ['resumes', resumeId],
    queryFn: () => resumesApi.getById(resumeId),
    refetchInterval: (q) => (q.state.data?.status === 'PENDING' || q.state.data?.status === 'PROCESSING' ? 1500 : false),
  })

  if (query.isLoading || !query.data) return <PageSpinner />
  const resume = query.data

  if (resume.status === 'PENDING' || resume.status === 'PROCESSING') {
    return (
      <Card>
        <CardContent className="flex items-center gap-3 py-8">
          <PageSpinner />
          <p className="text-sm text-ink-muted">Extracting skills and running ATS checks...</p>
        </CardContent>
      </Card>
    )
  }

  if (resume.status === 'FAILED') {
    return (
      <Card>
        <CardContent>
          <p className="text-sm font-medium text-danger">Processing failed</p>
          <p className="mt-1 text-sm text-ink-muted">{resume.errorMessage}</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <CardTitle>{resume.originalFilename}</CardTitle>
          {resume.inferredYearsExperience != null && (
            <Badge tone="brand">{resume.inferredYearsExperience} yrs inferred</Badge>
          )}
        </CardHeader>
        <CardContent>
          <p className="mb-2 text-sm font-semibold text-ink">Extracted skills ({resume.extractedSkills.length})</p>
          {resume.extractedSkills.length === 0 ? (
            <p className="text-sm text-ink-muted">No recognizable skills found in this document.</p>
          ) : (
            <div className="flex flex-wrap gap-1.5">
              {resume.extractedSkills.map((s) => (
                <Badge key={s.slug} tone="brand">
                  {s.name}
                  {s.occurrenceCount > 1 && <span className="text-brand-500">×{s.occurrenceCount}</span>}
                </Badge>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>ATS structural checks</CardTitle>
        </CardHeader>
        <CardContent>
          {resume.atsChecks.length === 0 ? (
            <p className="text-sm text-success">No structural issues detected.</p>
          ) : (
            <ul className="space-y-2.5">
              {resume.atsChecks.map((check, i) => {
                const Icon = severityIcon[check.severity]
                return (
                  <li key={i} className="flex items-start gap-2.5 text-sm">
                    <Icon className={cn('mt-0.5 size-4 shrink-0', severityIconClass[check.severity])} />
                    <div>
                      <span className="font-medium text-ink">{titleCase(check.type)}</span>
                      <p className="text-ink-muted">{check.message}</p>
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Writing suggestions</CardTitle>
        </CardHeader>
        <CardContent>
          {resume.suggestions.length === 0 ? (
            <p className="text-sm text-success">No suggestions -- strong bullet writing throughout.</p>
          ) : (
            <ul className="space-y-3">
              {resume.suggestions.map((s, i) => (
                <li key={i} className="rounded-control border border-border p-3">
                  <div className="flex items-center gap-2">
                    <Badge tone={severityTone[s.severity]}>{titleCase(s.category)}</Badge>
                  </div>
                  <p className="mt-1.5 text-sm text-ink">{s.message}</p>
                  {s.evidence && <p className="mt-1 text-xs italic text-ink-muted">"{s.evidence}"</p>}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <JobScoreCard resumeId={resume.id} />
    </div>
  )
}
