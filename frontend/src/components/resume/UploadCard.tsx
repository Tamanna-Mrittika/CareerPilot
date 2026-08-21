import { useMutation, useQueryClient } from '@tanstack/react-query'
import { UploadCloud } from 'lucide-react'
import { useRef, useState } from 'react'
import { resumesApi } from '@/lib/api/resumes'
import { Card, CardContent } from '@/components/ui/Card'
import { Spinner } from '@/components/ui/Spinner'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { cn } from '@/lib/utils'

const MAX_BYTES = 5 * 1024 * 1024

export function UploadCard({ onUploaded }: { onUploaded: (id: string) => void }) {
  const [dragOver, setDragOver] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()

  const uploadMutation = useMutation({
    mutationFn: resumesApi.upload,
    onSuccess: (summary) => {
      queryClient.invalidateQueries({ queryKey: ['resumes', 'mine'] })
      toast({ title: 'Resume uploaded', description: 'Processing in the background...', variant: 'success' })
      onUploaded(summary.id)
    },
    onError: (error) => toastFromError(error, 'Upload failed'),
  })

  const handleFile = (file: File | undefined) => {
    if (!file) return
    if (file.type !== 'application/pdf') {
      toast({ title: 'PDF only', description: 'Please upload a PDF resume.', variant: 'danger' })
      return
    }
    if (file.size > MAX_BYTES) {
      toast({ title: 'File too large', description: 'Resumes must be 5MB or smaller.', variant: 'danger' })
      return
    }
    uploadMutation.mutate(file)
  }

  return (
    <Card>
      <CardContent>
        <div
          onDragOver={(e) => {
            e.preventDefault()
            setDragOver(true)
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault()
            setDragOver(false)
            handleFile(e.dataTransfer.files[0])
          }}
          onClick={() => inputRef.current?.click()}
          className={cn(
            'flex cursor-pointer flex-col items-center justify-center gap-3 rounded-control border-2 border-dashed px-6 py-10 text-center transition-colors',
            dragOver ? 'border-brand-500 bg-brand-50/50' : 'border-border-strong hover:border-brand-400',
          )}
        >
          {uploadMutation.isPending ? (
            <Spinner className="size-8" />
          ) : (
            <UploadCloud className="size-8 text-brand-600" />
          )}
          <div>
            <p className="text-sm font-medium text-ink">
              {uploadMutation.isPending ? 'Uploading...' : 'Drop your resume here, or click to browse'}
            </p>
            <p className="mt-1 text-xs text-ink-muted">PDF only, up to 5MB</p>
          </div>
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf"
            className="hidden"
            onChange={(e) => handleFile(e.target.files?.[0])}
          />
        </div>
      </CardContent>
    </Card>
  )
}
