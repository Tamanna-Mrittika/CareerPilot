import { DndContext, DragOverlay, PointerSensor, useSensor, useSensors, type DragEndEvent, type DragStartEvent } from '@dnd-kit/core'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { trackerApi } from '@/lib/api/tracker'
import type { ApplicationResponse, ApplicationStatus, BoardResponse } from '@/lib/api/types'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { ApplicationCardContent } from './ApplicationCard'
import { KanbanColumn } from './KanbanColumn'

const COLUMN_ORDER: ApplicationStatus[] = ['WISHLIST', 'APPLIED', 'INTERVIEWING', 'OFFER', 'REJECTED', 'WITHDRAWN']

export function KanbanBoard({
  board,
  onCardClick,
}: {
  board: BoardResponse
  onCardClick: (application: ApplicationResponse) => void
}) {
  const [active, setActive] = useState<ApplicationResponse | null>(null)
  const queryClient = useQueryClient()

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }))

  const transitionMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ApplicationStatus }) => trackerApi.transition(id, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] }),
    onError: (error) => toastFromError(error, 'Could not move this application'),
  })

  const handleDragStart = (event: DragStartEvent) => {
    setActive(event.active.data.current?.application as ApplicationResponse)
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const application = active
    setActive(null)
    if (!application || !event.over) return

    const targetStatus = event.over.id as ApplicationStatus
    if (targetStatus === application.status) return

    if (!application.allowedTransitions.includes(targetStatus)) {
      toast({
        title: 'That move is not allowed',
        description: `An application in ${application.status} can only move to: ${application.allowedTransitions.join(', ') || 'nothing (terminal state)'}.`,
        variant: 'danger',
      })
      return
    }

    transitionMutation.mutate({ id: application.id, status: targetStatus })
  }

  return (
    <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
      <div className="flex gap-4 overflow-x-auto pb-2">
        {COLUMN_ORDER.map((status) => (
          <KanbanColumn
            key={status}
            status={status}
            applications={board.columns[status] ?? []}
            draggable={!active || active.allowedTransitions.includes(status) || active.status === status}
            onCardClick={onCardClick}
          />
        ))}
      </div>

      <DragOverlay>
        {active && <ApplicationCardContent application={active} isDragging />}
      </DragOverlay>
    </DndContext>
  )
}
