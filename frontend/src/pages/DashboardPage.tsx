import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Briefcase, FileText, KanbanSquare, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { profileApi } from '@/lib/api/profile'
import { resumesApi } from '@/lib/api/resumes'
import { trackerApi } from '@/lib/api/tracker'
import { matchesApi } from '@/lib/api/matches'
import type { ProfileResponse } from '@/lib/api/types'
import { useAuth } from '@/lib/auth/AuthContext'
import { Card, CardContent } from '@/components/ui/Card'
import { ScoreRing } from '@/components/ui/ScoreRing'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/Button'

function profileCompleteness(profile: ProfileResponse | undefined): number {
  if (!profile) return 0
  const checks = [
    !!profile.headline,
    !!profile.summary,
    !!profile.locationCity,
    !!profile.remotePreference,
    profile.education.length > 0,
    profile.experience.length > 0,
    profile.skills.length > 0,
    !!profile.phone,
  ]
  return Math.round((checks.filter(Boolean).length / checks.length) * 100)
}

export function DashboardPage() {
  const { user } = useAuth()

  const profileQuery = useQuery({ queryKey: ['profile', 'me'], queryFn: profileApi.getMe })
  const resumesQuery = useQuery({ queryKey: ['resumes', 'mine'], queryFn: resumesApi.listMine })
  const boardQuery = useQuery({ queryKey: ['tracker', 'board'], queryFn: trackerApi.board })
  const topMatchQuery = useQuery({
    queryKey: ['matches', 'all', 'top'],
    queryFn: () => matchesApi.all({ limit: 1 }),
  })

  const completeness = profileCompleteness(profileQuery.data)
  const activeApplications = boardQuery.data
    ? boardQuery.data.total -
      (boardQuery.data.columns.REJECTED?.length ?? 0) -
      (boardQuery.data.columns.WITHDRAWN?.length ?? 0)
    : undefined
  const topMatch = topMatchQuery.data?.matches[0]

  return (
    <div className="space-y-8">
      <div>
        <h2 className="text-2xl font-semibold text-ink">Welcome back, {user?.fullName?.split(' ')[0]}</h2>
        <p className="mt-1 text-sm text-ink-muted">Here's where things stand across your job search.</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardContent className="flex items-center gap-4">
            <ScoreRing score={profileQuery.isLoading ? null : completeness} size="md" />
            <div>
              <p className="text-sm text-ink-muted">Profile completeness</p>
              <Link to="/profile" className="text-sm font-medium text-brand-600 hover:text-brand-700">
                {completeness < 100 ? 'Finish your profile' : 'Looking great'}
              </Link>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="flex items-center gap-4">
            <div className="flex size-14 items-center justify-center rounded-full bg-brand-50">
              <FileText className="size-6 text-brand-600" />
            </div>
            <div>
              <p className="text-sm text-ink-muted">Resumes uploaded</p>
              {resumesQuery.isLoading ? (
                <Skeleton className="mt-1 h-6 w-8" />
              ) : (
                <p className="text-xl font-semibold text-ink">{resumesQuery.data?.length ?? 0}</p>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="flex items-center gap-4">
            <div className="flex size-14 items-center justify-center rounded-full bg-brand-50">
              <KanbanSquare className="size-6 text-brand-600" />
            </div>
            <div>
              <p className="text-sm text-ink-muted">Active applications</p>
              {boardQuery.isLoading ? (
                <Skeleton className="mt-1 h-6 w-8" />
              ) : (
                <p className="text-xl font-semibold text-ink">{activeApplications ?? 0}</p>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="flex items-center gap-4">
            {topMatchQuery.isLoading ? (
              <Skeleton className="size-14 rounded-full" />
            ) : (
              <ScoreRing score={topMatch?.overallScore ?? null} size="md" />
            )}
            <div>
              <p className="text-sm text-ink-muted">Best current match</p>
              <p className="truncate text-sm font-medium text-ink">{topMatch?.job.title ?? 'No matches yet'}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card hoverable className="lg:col-span-2">
          <CardContent>
            <div className="mb-4 flex items-center gap-2">
              <Sparkles className="size-4 text-brand-600" />
              <h3 className="font-semibold text-ink">Get the most out of CareerPilot</h3>
            </div>
            <ol className="space-y-3">
              {[
                { to: '/profile', label: 'Fill out your profile and skills', done: completeness >= 75 },
                { to: '/resume', label: 'Upload a resume for ATS scoring', done: (resumesQuery.data?.length ?? 0) > 0 },
                {
                  to: '/jobs/local',
                  label: 'Browse Dhaka postings ranked by fit',
                  done: false,
                },
                { to: '/tracker', label: 'Track applications on the Kanban board', done: (activeApplications ?? 0) > 0 },
              ].map((step) => (
                <li key={step.to}>
                  <Link
                    to={step.to}
                    className="flex items-center justify-between rounded-control border border-border px-4 py-3 transition-colors hover:border-brand-500 hover:bg-brand-50/40"
                  >
                    <span className="flex items-center gap-3 text-sm">
                      <span
                        className={`flex size-5 items-center justify-center rounded-full text-[10px] font-bold ${
                          step.done ? 'bg-success-bg text-success' : 'bg-surface-sunken text-ink-faint'
                        }`}
                      >
                        {step.done ? '✓' : ''}
                      </span>
                      {step.label}
                    </span>
                    <ArrowRight className="size-4 text-ink-faint" />
                  </Link>
                </li>
              ))}
            </ol>
          </CardContent>
        </Card>

        <Card hoverable>
          <CardContent>
            <div className="mb-4 flex items-center gap-2">
              <Briefcase className="size-4 text-brand-600" />
              <h3 className="font-semibold text-ink">Local job market</h3>
            </div>
            <p className="text-sm text-ink-muted">
              Real Dhaka postings are aggregated across multiple job boards and ranked against your profile.
            </p>
            <Link to="/jobs/local" className={buttonClasses('outline', 'md', 'mt-4 w-full')}>
              Browse Dhaka jobs
            </Link>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
