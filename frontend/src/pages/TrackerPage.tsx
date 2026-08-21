import { useQuery } from '@tanstack/react-query'
import { KanbanSquare } from 'lucide-react'
import { useState } from 'react'
import { trackerApi } from '@/lib/api/tracker'
import type { ApplicationResponse } from '@/lib/api/types'
import { useAuth } from '@/lib/auth/AuthContext'
import { EmptyState } from '@/components/ui/EmptyState'
import { PageSpinner } from '@/components/ui/Spinner'
import { KanbanBoard } from '@/components/tracker/KanbanBoard'
import { NewApplicationDialog } from '@/components/tracker/NewApplicationDialog'
import { ApplicationDetailDialog } from '@/components/tracker/ApplicationDetailDialog'
import { SimulateEmailPanel } from '@/components/tracker/SimulateEmailPanel'

export function TrackerPage() {
  const { isAdmin } = useAuth()
  const [selected, setSelected] = useState<ApplicationResponse | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const boardQuery = useQuery({ queryKey: ['tracker', 'board'], queryFn: trackerApi.board })

  if (boardQuery.isLoading || !boardQuery.data) return <PageSpinner />

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-muted">
          Drag a card between columns, or click it to add notes. Only the moves your board allows will succeed.
        </p>
        <NewApplicationDialog />
      </div>

      {boardQuery.data.total === 0 ? (
        <EmptyState
          icon={KanbanSquare}
          title="Nothing tracked yet"
          description="Add an application, or add one straight from a job's fit-score detail."
        />
      ) : (
        <KanbanBoard
          board={boardQuery.data}
          onCardClick={(app) => {
            setSelected(app)
            setDetailOpen(true)
          }}
        />
      )}

      {isAdmin && <SimulateEmailPanel />}

      <ApplicationDetailDialog application={selected} open={detailOpen} onOpenChange={setDetailOpen} />
    </div>
  )
}
