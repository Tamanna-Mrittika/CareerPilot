import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { profileApi } from '@/lib/api/profile'
import type { ProfileResponse } from '@/lib/api/types'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/Select'
import { toast, toastFromError } from '@/components/ui/toast-store'

const schema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  headline: z.string().optional(),
  summary: z.string().optional(),
  email: z.string().email('Enter a valid email').optional().or(z.literal('')),
  phone: z.string().optional(),
  locationCity: z.string().optional(),
  locationCountry: z.string().optional(),
  remotePreference: z.enum(['ONSITE', 'HYBRID', 'REMOTE', 'ANY']).optional(),
  linkedinUrl: z.string().url('Enter a valid URL').optional().or(z.literal('')),
  githubUrl: z.string().url('Enter a valid URL').optional().or(z.literal('')),
  portfolioUrl: z.string().url('Enter a valid URL').optional().or(z.literal('')),
})

type FormValues = z.infer<typeof schema>

export function BasicsForm({ profile }: { profile: ProfileResponse }) {
  const queryClient = useQueryClient()

  const { register, handleSubmit, control, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      fullName: profile.fullName,
      headline: profile.headline ?? '',
      summary: profile.summary ?? '',
      email: profile.email ?? '',
      phone: profile.phone ?? '',
      locationCity: profile.locationCity ?? '',
      locationCountry: profile.locationCountry ?? '',
      remotePreference: profile.remotePreference ?? undefined,
      linkedinUrl: profile.linkedinUrl ?? '',
      githubUrl: profile.githubUrl ?? '',
      portfolioUrl: profile.portfolioUrl ?? '',
    },
  })

  const mutation = useMutation({
    mutationFn: profileApi.updateMe,
    onSuccess: (updated) => {
      queryClient.setQueryData(['profile', 'me'], updated)
      toast({ title: 'Profile saved', variant: 'success' })
    },
    onError: (error) => toastFromError(error, 'Could not save profile'),
  })

  const onSubmit = (values: FormValues) => {
    mutation.mutate({
      ...values,
      email: values.email || null,
      linkedinUrl: values.linkedinUrl || null,
      githubUrl: values.githubUrl || null,
      portfolioUrl: values.portfolioUrl || null,
    })
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <Label htmlFor="fullName">Full name</Label>
          <Input id="fullName" {...register('fullName')} />
        </div>
        <div>
          <Label htmlFor="headline">Headline</Label>
          <Input id="headline" placeholder="Backend Engineer" {...register('headline')} />
        </div>
      </div>

      <div>
        <Label htmlFor="summary">Summary</Label>
        <Textarea id="summary" rows={4} placeholder="A short professional summary..." {...register('summary')} />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <Label htmlFor="email">Contact email</Label>
          <Input id="email" type="email" {...register('email')} />
          {formState.errors.email && <p className="mt-1 text-xs text-danger">{formState.errors.email.message}</p>}
        </div>
        <div>
          <Label htmlFor="phone">Phone</Label>
          <Input id="phone" {...register('phone')} />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div>
          <Label htmlFor="locationCity">City</Label>
          <Input id="locationCity" placeholder="Dhaka" {...register('locationCity')} />
        </div>
        <div>
          <Label htmlFor="locationCountry">Country</Label>
          <Input id="locationCountry" placeholder="Bangladesh" {...register('locationCountry')} />
        </div>
        <div>
          <Label>Remote preference</Label>
          <Controller
            control={control}
            name="remotePreference"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select..." />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ONSITE">On-site</SelectItem>
                  <SelectItem value="HYBRID">Hybrid</SelectItem>
                  <SelectItem value="REMOTE">Remote</SelectItem>
                  <SelectItem value="ANY">No preference</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div>
          <Label htmlFor="linkedinUrl">LinkedIn</Label>
          <Input id="linkedinUrl" placeholder="https://linkedin.com/in/..." {...register('linkedinUrl')} />
        </div>
        <div>
          <Label htmlFor="githubUrl">GitHub</Label>
          <Input id="githubUrl" placeholder="https://github.com/..." {...register('githubUrl')} />
        </div>
        <div>
          <Label htmlFor="portfolioUrl">Portfolio</Label>
          <Input id="portfolioUrl" placeholder="https://..." {...register('portfolioUrl')} />
        </div>
      </div>

      <div className="flex justify-end">
        <Button type="submit" loading={mutation.isPending}>
          Save changes
        </Button>
      </div>
    </form>
  )
}
