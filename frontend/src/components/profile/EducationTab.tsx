import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { GraduationCap, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { profileApi } from '@/lib/api/profile'
import type { EducationEntry, ProfileResponse } from '@/lib/api/types'
import { Button } from '@/components/ui/Button'
import { Card, CardContent } from '@/components/ui/Card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/Dialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { Input } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { formatDate } from '@/lib/utils'

const schema = z.object({
  institution: z.string().min(1, 'Institution is required'),
  degree: z.string().optional(),
  fieldOfStudy: z.string().optional(),
  startDate: z.string().optional(),
  endDate: z.string().optional(),
  grade: z.string().optional(),
  description: z.string().optional(),
})

type FormValues = z.infer<typeof schema>

export function EducationTab({ profile }: { profile: ProfileResponse }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()

  const { register, handleSubmit, reset, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const addMutation = useMutation({
    mutationFn: profileApi.addEducation,
    onSuccess: (entry: EducationEntry) => {
      queryClient.setQueryData<ProfileResponse>(['profile', 'me'], (prev) =>
        prev ? { ...prev, education: [...prev.education, entry] } : prev,
      )
      toast({ title: 'Education added', variant: 'success' })
      reset()
      setOpen(false)
    },
    onError: (error) => toastFromError(error, 'Could not add education'),
  })

  const removeMutation = useMutation({
    mutationFn: profileApi.removeEducation,
    onSuccess: (_data, id) => {
      queryClient.setQueryData<ProfileResponse>(['profile', 'me'], (prev) =>
        prev ? { ...prev, education: prev.education.filter((e) => e.id !== id) } : prev,
      )
    },
    onError: (error) => toastFromError(error, 'Could not remove education'),
  })

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus className="size-4" />
          Add education
        </Button>
      </div>

      {profile.education.length === 0 ? (
        <EmptyState icon={GraduationCap} title="No education added yet" description="Add your degrees and certifications." />
      ) : (
        <div className="space-y-3">
          {profile.education.map((entry) => (
            <Card key={entry.id}>
              <CardContent className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-medium text-ink">{entry.institution}</p>
                  <p className="text-sm text-ink-muted">
                    {[entry.degree, entry.fieldOfStudy].filter(Boolean).join(', ')}
                  </p>
                  <p className="mt-1 text-xs text-ink-faint">
                    {[formatDate(entry.startDate), formatDate(entry.endDate) ?? 'Present'].filter(Boolean).join(' – ')}
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

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add education</DialogTitle>
          </DialogHeader>
          <form
            onSubmit={handleSubmit((values) =>
              addMutation.mutate({
                institution: values.institution,
                degree: values.degree || null,
                fieldOfStudy: values.fieldOfStudy || null,
                startDate: values.startDate || null,
                endDate: values.endDate || null,
                grade: values.grade || null,
                description: values.description || null,
              }),
            )}
            className="space-y-4"
          >
            <div>
              <Label htmlFor="institution">Institution</Label>
              <Input id="institution" {...register('institution')} />
              {formState.errors.institution && (
                <p className="mt-1 text-xs text-danger">{formState.errors.institution.message}</p>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="degree">Degree</Label>
                <Input id="degree" placeholder="BSc" {...register('degree')} />
              </div>
              <div>
                <Label htmlFor="fieldOfStudy">Field of study</Label>
                <Input id="fieldOfStudy" placeholder="Computer Science" {...register('fieldOfStudy')} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="startDate">Start date</Label>
                <Input id="startDate" type="date" {...register('startDate')} />
              </div>
              <div>
                <Label htmlFor="endDate">End date</Label>
                <Input id="endDate" type="date" {...register('endDate')} />
              </div>
            </div>
            <div>
              <Label htmlFor="grade">Grade</Label>
              <Input id="grade" placeholder="3.85 CGPA" {...register('grade')} />
            </div>
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
