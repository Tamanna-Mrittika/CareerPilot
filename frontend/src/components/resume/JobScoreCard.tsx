import { useMutation, useQuery } from '@tanstack/react-query'
import { Search, Target } from 'lucide-react'
import { useState } from 'react'
import { useDebounce } from '@/lib/use-debounce'
import { jobsApi } from '@/lib/api/jobs'
import { resumesApi } from '@/lib/api/resumes'
import type { ResumeScoreResponse } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Input } from '@/components/ui/Input'
import { ScoreRing } from '@/components/ui/ScoreRing'
import { Spinner } from '@/components/ui/Spinner'
import { toastFromError } from '@/components/ui/toast-store'

export function JobScoreCard({ resumeId }: { resumeId: string }) {
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<ResumeScoreResponse | null>(null)
  const debouncedQuery = useDebounce(query, 350)

  const searchQuery = useQuery({
    queryKey: ['jobs', 'search-for-score', debouncedQuery],
    queryFn: () => jobsApi.search({ q: debouncedQuery, scope: 'ALL', size: 6 }),
    enabled: debouncedQuery.trim().length > 1,
  })

  const scoreMutation = useMutation({
    mutationFn: (jobId: string) => resumesApi.scoreAgainstJob(resumeId, jobId),
    onSuccess: setResult,
    onError: (error) => toastFromError(error, 'Could not score against that job'),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Score against a specific job</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-faint" />
          <Input
            placeholder="Search a job title to compare against..."
            className="pl-9"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        {searchQuery.data && searchQuery.data.content.length > 0 && (
          <div className="mt-3 space-y-1.5">
            {searchQuery.data.content.map((job) => (
              <button
                key={job.id}
                onClick={() => scoreMutation.mutate(job.id)}
                disabled={scoreMutation.isPending}
                className="flex w-full items-center justify-between rounded-control border border-border px-3 py-2 text-left text-sm transition-colors hover:border-brand-500 hover:bg-brand-50/40 disabled:opacity-50"
              >
                <span>
                  <span className="font-medium text-ink">{job.title}</span>
                  <span className="text-ink-muted"> · {job.company}</span>
                </span>
                <Target className="size-4 text-brand-600" />
              </button>
            ))}
          </div>
        )}

        {scoreMutation.isPending && (
          <div className="mt-4 flex items-center gap-2 text-sm text-ink-muted">
            <Spinner /> Scoring...
          </div>
        )}

        {result && (
          <div className="mt-5 rounded-control border border-border p-4">
            <div className="flex items-center gap-4">
              <ScoreRing score={result.overallScore * 100} size="md" />
              <div>
                <p className="font-medium text-ink">{result.jobTitle}</p>
                <p className="text-sm text-ink-muted">{result.jobCompany}</p>
              </div>
            </div>

            {result.actionableGaps.length > 0 && (
              <div className="mt-4">
                <p className="mb-1.5 text-sm font-semibold text-ink">Add these terms to improve your match</p>
                <div className="flex flex-wrap gap-1.5">
                  {result.actionableGaps.map((t) => (
                    <Badge key={t.term} tone="warning">
                      {t.term}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
