import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { useState } from 'react'
import { trackerApi } from '@/lib/api/tracker'
import type { WebhookResultResponse } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card'
import { Input, Textarea } from '@/components/ui/Input'
import { toastFromError } from '@/components/ui/toast-store'
import { titleCase } from '@/lib/utils'

export function SimulateEmailPanel() {
  const [subject, setSubject] = useState('Interview invitation for Backend Engineer')
  const [body, setBody] = useState('We would like to invite you to an interview next week.')
  const [result, setResult] = useState<WebhookResultResponse | null>(null)
  const queryClient = useQueryClient()

  const mutation = useMutation({
    mutationFn: () => trackerApi.simulateEmail({ subject, body, from: 'recruiter@example.com' }),
    onSuccess: (res) => {
      setResult(res)
      if (res.matched) queryClient.invalidateQueries({ queryKey: ['tracker', 'board'] })
    },
    onError: (error) => toastFromError(error, 'Simulation failed'),
  })

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Mail className="size-4 text-brand-600" />
          <CardTitle>Simulate an email (Admin)</CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-xs text-ink-muted">
          Demonstrates the HMAC-verified email webhook's auto-transition classifier without needing a real inbox.
          It moves the most recently updated matching application in your board.
        </p>
        <Input value={subject} onChange={(e) => setSubject(e.target.value)} placeholder="Subject" />
        <Textarea rows={3} value={body} onChange={(e) => setBody(e.target.value)} placeholder="Body" />
        <Button size="sm" onClick={() => mutation.mutate()} loading={mutation.isPending}>
          Send simulated email
        </Button>
        {result && (
          <div className="rounded-control border border-border p-3 text-sm">
            <p className="font-medium text-ink">{result.matched ? 'Classified and applied' : 'No confident match'}</p>
            {result.matched && result.newStatus && (
              <p className="mt-1 text-ink-muted">
                {result.previousStatus && `${titleCase(result.previousStatus)} → `}
                <Badge tone="brand">{titleCase(result.newStatus)}</Badge>
              </p>
            )}
            {result.matchedPhrase && <p className="mt-1 text-xs italic text-ink-muted">Matched: "{result.matchedPhrase}"</p>}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
