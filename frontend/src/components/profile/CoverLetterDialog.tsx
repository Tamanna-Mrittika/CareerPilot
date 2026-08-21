import { useMutation } from '@tanstack/react-query'
import { FileDown } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { profileApi } from '@/lib/api/profile'
import { Button } from '@/components/ui/Button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/Dialog'
import { Input, Textarea } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { toastFromError } from '@/components/ui/toast-store'

interface FormValues {
  companyName: string
  jobTitle: string
  hiringManager: string
  customBody: string
}

export function CoverLetterDialog() {
  const [open, setOpen] = useState(false)
  const { register, handleSubmit, reset } = useForm<FormValues>()

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      profileApi.generateCoverLetter({
        companyName: values.companyName || null,
        jobTitle: values.jobTitle || null,
        hiringManager: values.hiringManager || null,
        customBody: values.customBody || null,
      }),
    onSuccess: (response, values) => {
      const url = URL.createObjectURL(response.data as Blob)
      const link = document.createElement('a')
      link.href = url
      const safeCompany = (values.companyName || 'cover-letter').toLowerCase().replace(/[^a-z0-9]+/g, '-')
      link.download = `cover-letter-${safeCompany}.pdf`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
      setOpen(false)
      reset()
    },
    onError: (error) => toastFromError(error, 'Could not generate the cover letter'),
  })

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline">
          <FileDown className="size-4" />
          Generate cover letter
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Generate a cover letter</DialogTitle>
          <DialogDescription>Built from your profile data as a downloadable PDF.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="companyName">Company</Label>
              <Input id="companyName" placeholder="Acme Inc." {...register('companyName')} />
            </div>
            <div>
              <Label htmlFor="jobTitle">Job title</Label>
              <Input id="jobTitle" placeholder="Backend Engineer" {...register('jobTitle')} />
            </div>
          </div>
          <div>
            <Label htmlFor="hiringManager">Hiring manager (optional)</Label>
            <Input id="hiringManager" placeholder="Jane Smith" {...register('hiringManager')} />
          </div>
          <div>
            <Label htmlFor="customBody">Additional notes (optional)</Label>
            <Textarea id="customBody" rows={3} placeholder="Anything you'd like woven in..." {...register('customBody')} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={mutation.isPending}>
              Download PDF
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
