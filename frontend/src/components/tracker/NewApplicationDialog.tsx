import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { trackerApi } from '@/lib/api/tracker'
import { Button } from '@/components/ui/Button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/Dialog'
import { Input, Textarea } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { toast, toastFromError } from '@/components/ui/toast-store'

interface FormValues {
  jobTitle: string
  company: string
  applyUrl: string
  location: string
  notes: string
}

export function NewApplicationDialog() {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, formState } = useForm<FormValues>()

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      trackerApi.create({
        jobTitle: values.jobTitle,
        company: values.company,
        applyUrl: values.applyUrl || null,
        location: values.location || null,
        notes: values.notes || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] })
      toast({ title: 'Application added', variant: 'success' })
      reset()
      setOpen(false)
    },
    onError: (error) => toastFromError(error, 'Could not add this application'),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="size-4" />
          New application
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add an application</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="jobTitle">Job title</Label>
              <Input id="jobTitle" {...register('jobTitle', { required: true })} />
              {formState.errors.jobTitle && <p className="mt-1 text-xs text-danger">Required</p>}
            </div>
            <div>
              <Label htmlFor="company">Company</Label>
              <Input id="company" {...register('company', { required: true })} />
              {formState.errors.company && <p className="mt-1 text-xs text-danger">Required</p>}
            </div>
          </div>
          <div>
            <Label htmlFor="location">Location</Label>
            <Input id="location" placeholder="Dhaka, Bangladesh" {...register('location')} />
          </div>
          <div>
            <Label htmlFor="applyUrl">Apply URL</Label>
            <Input id="applyUrl" placeholder="https://..." {...register('applyUrl')} />
          </div>
          <div>
            <Label htmlFor="notes">Notes</Label>
            <Textarea id="notes" rows={3} {...register('notes')} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={mutation.isPending}>
              Add to Wishlist
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
