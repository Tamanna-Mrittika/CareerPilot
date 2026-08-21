import { useQuery } from '@tanstack/react-query'
import { FileText } from 'lucide-react'
import { useEffect, useState } from 'react'
import { resumesApi } from '@/lib/api/resumes'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { UploadCard } from '@/components/resume/UploadCard'
import { ResumeDetail } from '@/components/resume/ResumeDetail'
import { cn, formatRelativeTime } from '@/lib/utils'

const statusTone = {
  PENDING: 'neutral',
  PROCESSING: 'brand',
  COMPLETED: 'success',
  FAILED: 'danger',
} as const

export function ResumePage() {
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const resumesQuery = useQuery({
    queryKey: ['resumes', 'mine'],
    queryFn: resumesApi.listMine,
    refetchInterval: 4000,
  })

  useEffect(() => {
    if (!selectedId && resumesQuery.data && resumesQuery.data.length > 0) {
      setSelectedId(resumesQuery.data[0].id)
    }
  }, [resumesQuery.data, selectedId])

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
      <div className="space-y-4">
        <UploadCard onUploaded={setSelectedId} />

        {resumesQuery.data && resumesQuery.data.length > 0 && (
          <div className="space-y-2">
            {resumesQuery.data.map((r) => (
              <button
                key={r.id}
                onClick={() => setSelectedId(r.id)}
                className={cn(
                  'flex w-full items-center gap-3 rounded-control border px-3 py-2.5 text-left transition-colors',
                  selectedId === r.id ? 'border-brand-500 bg-brand-50/50' : 'border-border hover:border-brand-300',
                )}
              >
                <FileText className="size-4 shrink-0 text-ink-faint" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-ink">{r.originalFilename}</p>
                  <p className="text-xs text-ink-muted">{formatRelativeTime(r.uploadedAt)}</p>
                </div>
                <Badge tone={statusTone[r.status]}>{r.status}</Badge>
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        {!selectedId ? (
          <Card>
            <CardContent>
              <EmptyState
                icon={FileText}
                title="No resume selected"
                description="Upload a PDF resume to get an ATS score, extracted skills, and writing feedback."
              />
            </CardContent>
          </Card>
        ) : (
          <ResumeDetail resumeId={selectedId} />
        )}
      </div>
    </div>
  )
}
