import { client, unwrap } from './client'
import type { MatchPageResponse, MatchResponse, SkillGapResponse } from './types'

export interface MatchQueryParams {
  q?: string
  city?: string
  limit?: number
}

export const matchesApi = {
  local: (params: MatchQueryParams) => unwrap(client.get<MatchPageResponse>('/matches/local', { params })),
  remote: (params: MatchQueryParams) => unwrap(client.get<MatchPageResponse>('/matches/remote', { params })),
  all: (params: MatchQueryParams) => unwrap(client.get<MatchPageResponse>('/matches', { params })),
  forJob: (jobId: string) => unwrap(client.get<MatchResponse>(`/matches/jobs/${jobId}`)),
}

export const skillGapApi = {
  get: (params: { scope?: 'LOCAL' | 'REMOTE' | 'ALL'; city?: string }) =>
    unwrap(client.get<SkillGapResponse>('/skill-gap', { params })),
}
