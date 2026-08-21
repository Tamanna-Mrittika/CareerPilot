import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'

export function Pagination({
  page,
  totalPages,
  onPageChange,
}: {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}) {
  if (totalPages <= 1) return null

  return (
    <div className="flex items-center justify-center gap-3">
      <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
        <ChevronLeft className="size-4" />
        Previous
      </Button>
      <span className="text-sm text-ink-muted">
        Page <span className="font-medium text-ink">{page + 1}</span> of {totalPages}
      </span>
      <Button variant="outline" size="sm" onClick={() => onPageChange(page + 1)} disabled={page >= totalPages - 1}>
        Next
        <ChevronRight className="size-4" />
      </Button>
    </div>
  )
}
