import { useDraggable } from '@dnd-kit/core'
import { Mail } from 'lucide-react'
import type { ApplicationResponse } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { cn } from '@/lib/utils'

export function ApplicationCardContent({ application, isDragging }: { application: ApplicationResponse; isDragging?: boolean }) {
  const lastEvent = application.events.at(-1)
  const autoMoved = lastEvent?.source === 'EMAIL_WEBHOOK'

  return (
    <div
      className={cn(
        'space-y-1.5 rounded-control border border-border bg-surface p-3 shadow-card transition-shadow',
        'hover:border-brand-300 hover:shadow-card-hover',
        isDragging && 'opacity-50',
      )}
    >
      <p className="text-sm font-medium leading-snug text-ink">{application.jobTitle}</p>
      <p className="text-xs text-ink-muted">{application.company}</p>
      {autoMoved && (
        <Badge tone="brand" className="mt-1">
          <Mail className="size-3" /> Auto-moved by email
        </Badge>
      )}
    </div>
  )
}

/** The draggable card that lives inside a KanbanColumn. Not used for the DragOverlay clone
 *  -- that renders ApplicationCardContent directly, since calling useDraggable twice with
 *  the same id (once here, once in the overlay) would register two draggables for one item. */
export function ApplicationCard({
  application,
  onClick,
}: {
  application: ApplicationResponse
  onClick: () => void
}) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: application.id,
    data: { application },
  })

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      onClick={onClick}
      style={transform ? { transform: `translate(${transform.x}px, ${transform.y}px)`, zIndex: 50 } : undefined}
      className={cn('cursor-grab active:cursor-grabbing', isDragging && 'opacity-40')}
    >
      <ApplicationCardContent application={application} />
    </div>
  )
}
