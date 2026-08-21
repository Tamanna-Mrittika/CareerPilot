import { useDroppable } from '@dnd-kit/core'
import type { ApplicationResponse, ApplicationStatus } from '@/lib/api/types'
import { cn, titleCase } from '@/lib/utils'
import { ApplicationCard } from './ApplicationCard'

export function KanbanColumn({
  status,
  applications,
  draggable,
  onCardClick,
}: {
  status: ApplicationStatus
  applications: ApplicationResponse[]
  draggable: boolean
  onCardClick: (application: ApplicationResponse) => void
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status, disabled: !draggable })

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'flex w-72 shrink-0 flex-col rounded-card border border-border bg-surface-sunken/60 p-2.5 transition-colors',
        isOver && draggable && 'border-brand-500 bg-brand-50/50',
        isOver && !draggable && 'border-danger bg-danger-bg/50',
      )}
    >
      <div className="mb-2 flex items-center justify-between px-1">
        <p className="text-sm font-semibold text-ink">{titleCase(status)}</p>
        <span className="rounded-full bg-surface px-2 py-0.5 text-xs font-medium text-ink-muted">
          {applications.length}
        </span>
      </div>
      <div className="min-h-16 flex-1 space-y-2">
        {applications.map((app) => (
          <ApplicationCard key={app.id} application={app} onClick={() => onCardClick(app)} />
        ))}
      </div>
    </div>
  )
}
