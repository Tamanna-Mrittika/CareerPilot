import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ExternalLink, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { trackerApi } from '@/lib/api/tracker'
import type { ApplicationResponse, ApplicationStatus } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog'
import { Textarea } from '@/components/ui/Input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/Select'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { formatDate, titleCase } from '@/lib/utils'

export function ApplicationDetailDialog({
  application,
  open,
  onOpenChange,
}: {
  application: ApplicationResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const [notes, setNotes] = useState('')
  const queryClient = useQueryClient()

  useEffect(() => {
    setNotes(application?.notes ?? '')
  }, [application])

  const saveNotes = useMutation({
    mutationFn: (value: string) => {
      if (!application) throw new Error('No application selected')
      return trackerApi.updateNotes(application.id, value)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] })
      toast({ title: 'Notes saved', variant: 'success' })
    },
    onError: (error) => toastFromError(error, 'Could not save notes'),
  })

  const transition = useMutation({
    mutationFn: (status: ApplicationStatus) => {
      if (!application) throw new Error('No application selected')
      return trackerApi.transition(application.id, { status })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] })
      toast({ title: 'Status updated', variant: 'success' })
      // The application prop is a snapshot from the board query at open-time and won't
      // reflect the new status/allowedTransitions until that query refetches, so close
      // rather than leave the dialog showing stale state.
      onOpenChange(false)
    },
    onError: (error) => toastFromError(error, 'Could not move this application'),
  })

  const remove = useMutation({
    mutationFn: () => {
      if (!application) throw new Error('No application selected')
      return trackerApi.remove(application.id)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] })
      onOpenChange(false)
    },
    onError: (error) => toastFromError(error, 'Could not remove this application'),
  })

  if (!application) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{application.jobTitle}</DialogTitle>
          <p className="mt-1 text-sm text-ink-muted">{application.company}</p>
          <div className="mt-2 flex items-center gap-2">
            <Badge tone="brand">{titleCase(application.status)}</Badge>
            {application.applyUrl && (
              <a
                href={application.applyUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-xs font-medium text-brand-600 hover:text-brand-700"
              >
                Original posting <ExternalLink className="size-3" />
              </a>
            )}
          </div>
        </DialogHeader>

        {application.allowedTransitions.length > 0 && (
          <div>
            <label className="mb-1.5 block text-sm font-medium text-ink">Move to</label>
            <Select onValueChange={(v) => transition.mutate(v as ApplicationStatus)}>
              <SelectTrigger>
                <SelectValue placeholder="Choose a status..." />
              </SelectTrigger>
              <SelectContent>
                {application.allowedTransitions.map((s) => (
                  <SelectItem key={s} value={s}>
                    {titleCase(s)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <div>
          <label className="mb-1.5 block text-sm font-medium text-ink">Notes</label>
          <Textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </div>

        <div className="mt-5">
          <p className="mb-2 text-sm font-semibold text-ink">History</p>
          <ol className="space-y-2 border-l border-border pl-4">
            {application.events.map((event, i) => (
              <li key={i} className="relative text-sm">
                <span className="absolute -left-[21px] top-1.5 size-2 rounded-full bg-brand-500" />
                <span className="font-medium text-ink">
                  {event.fromStatus ? `${titleCase(event.fromStatus)} → ` : ''}
                  {titleCase(event.toStatus)}
                </span>
                {event.source === 'EMAIL_WEBHOOK' && (
                  <Badge tone="neutral" className="ml-2">
                    via email
                  </Badge>
                )}
                <p className="text-xs text-ink-muted">{formatDate(event.createdAt)}</p>
                {event.note && <p className="mt-0.5 text-xs italic text-ink-muted">"{event.note}"</p>}
              </li>
            ))}
          </ol>
        </div>

        <DialogFooter className="justify-between sm:justify-between">
          <Button variant="ghost" onClick={() => remove.mutate()} loading={remove.isPending}>
            <Trash2 className="size-4 text-danger" />
            Delete
          </Button>
          <Button onClick={() => saveNotes.mutate(notes)} loading={saveNotes.isPending}>
            Save notes
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
