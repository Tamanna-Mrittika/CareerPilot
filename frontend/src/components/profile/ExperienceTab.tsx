import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Briefcase, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { profileApi } from '@/lib/api/profile'
import type { ExperienceEntry, ProfileResponse } from '@/lib/api/types'
import { Button } from '@/components/ui/Button'
import { Card, CardContent } from '@/components/ui/Card'
import { Checkbox } from '@/components/ui/Checkbox'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { Input } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { formatDate } from '@/lib/utils'

const schema = z.object({
  company: z.string().min(1, 'Company is required'),
  title: z.string().min(1, 'Title is required'),
  employmentType: z.string().optional(),
  locationCity: z.string().optional(),
  startDate: z.string().min(1, 'Start date is required'),
  endDate: z.string().optional(),
  current: z.boolean().optional(),
  description: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

export function ExperienceTab({ profile }: { profile: ProfileResponse }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()

  const { register, handleSubmit, control, watch, reset, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { current: false },
  })
  const isCurrent = watch('current')

  const addMutation = useMutation({
    mutationFn: profileApi.addExperience,
    onSuccess: (entry: ExperienceEntry) => {
      queryClient.setQueryData<ProfileResponse>(['profile', 'me'], (prev) =>
        prev ? { ...prev, experience: [...prev.experience, entry] } : prev,
      )
      // Server recalculates total years -- refetch so the header stat stays correct.
      queryClient.invalidateQueries({ queryKey: ['profile', 'me'] })
      toast({ title: 'Experience added', variant: 'success' })
      reset({ current: false })
      setOpen(false)
    },
    onError: (error) => toastFromError(error, 'Could not add experience'),
  })

  const removeMutation = useMutation({
    mutationFn: profileApi.removeExperience,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile', 'me'] })
    },
    onError: (error) => toastFromError(error, 'Could not remove experience'),
  })

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus className="size-4" />
          Add experience
        </Button>
      </div>

      {profile.experience.length === 0 ? (
        <EmptyState icon={Briefcase} title="No experience added yet" description="Add roles to improve your fit scores." />
      ) : (
        <div className="space-y-3">
          {profile.experience.map((entry) => (
            <Card key={entry.id}>
              <CardContent className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium text-ink">
                    {entry.title} · {entry.company}
                  </p>
                  <p className="text-sm text-ink-muted">{entry.locationCity}</p>
                  <p className="mt-1 text-xs text-ink-faint">
                    {formatDate(entry.startDate)} – {entry.current ? 'Present' : formatDate(entry.endDate) ?? 'Present'}
                  </p>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => removeMutation.mutate(entry.id)}
                  disabled={removeMutation.isPending}
                >
                  <Trash2 className="size-4 text-danger" />
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog
        open={open}
        onOpenChange={(next) => {
          setOpen(next)
          if (!next) reset({ current: false })
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add experience</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleSubmit((values) =>
              addMutation.mutate({
                company: values.company,
                title: values.title,
                employmentType: values.employmentType || null,
                locationCity: values.locationCity || null,
                startDate: values.startDate,
                endDate: values.current ? null : values.endDate || null,
                current: values.current ?? false,
                description: values.description || null,
              }),
            )}
            className="space-y-4"
          >
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="title">Title</Label>
                <Input id="title" placeholder="Backend Engineer" {...register('title')} />
                {formState.errors.title && <p className="mt-1 text-xs text-danger">{formState.errors.title.message}</p>}
              </div>
              <div>
                <Label htmlFor="company">Company</Label>
                <Input id="company" {...register('company')} />
                {formState.errors.company && (
                  <p className="mt-1 text-xs text-danger">{formState.errors.company.message}</p>
                )}
              </div>
            </div>
            <div>
              <Label htmlFor="locationCity">Location</Label>
              <Input id="locationCity" placeholder="Dhaka" {...register('locationCity')} />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="startDate">Start date</Label>
                <Input id="startDate" type="date" {...register('startDate')} />
                {formState.errors.startDate && (
                  <p className="mt-1 text-xs text-danger">{formState.errors.startDate.message}</p>
                )}
              </div>
              <div>
                <Label htmlFor="endDate">End date</Label>
                <Input id="endDate" type="date" disabled={isCurrent} {...register('endDate')} />
              </div>
            </div>
            <Controller
              control={control}
              name="current"
              render={({ field }) => (
                <label className="flex items-center gap-2 text-sm text-ink">
                  <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                  <span>I currently work here</span>
                </label>
              )}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={addMutation.isPending}>
                Add
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
