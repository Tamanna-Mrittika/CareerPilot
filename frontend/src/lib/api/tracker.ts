import { client, unwrap } from './client'
import type {
  ApplicationResponse,
  BoardResponse,
  CreateApplicationRequest,
  EmailWebhookRequest,
  StatusMachine,
  TransitionRequest,
  WebhookResultResponse,
} from './types'

export const trackerApi = {
  board: () => unwrap(client.get<BoardResponse>('/applications')),
  create: (body: CreateApplicationRequest) => unwrap(client.post<ApplicationResponse>('/applications', body)),
  getById: (id: string) => unwrap(client.get<ApplicationResponse>(`/applications/${id}`)),
  transition: (id: string, body: TransitionRequest) =>
    unwrap(client.patch<ApplicationResponse>(`/applications/${id}/status`, body)),
  updateNotes: (id: string, notes: string) =>
    unwrap(client.patch<ApplicationResponse>(`/applications/${id}/notes`, { notes })),
  remove: (id: string) => client.delete(`/applications/${id}`),
  statuses: () => unwrap(client.get<StatusMachine>('/applications/statuses')),
  simulateEmail: (body: EmailWebhookRequest) =>
    unwrap(client.post<WebhookResultResponse>('/webhooks/email/simulate', body)),
}
