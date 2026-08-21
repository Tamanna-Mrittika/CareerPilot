import { client, unwrap } from './client'
import type { IngestionStatsResponse, JobPage, JobResponse } from './types'

export interface JobSearchParams {
  q?: string
  city?: string
  country?: string
  source?: string
  employmentType?: string
  minSalary?: number
  page?: number
  size?: number
  sort?: string
}

export const jobsApi = {
  local: (params: JobSearchParams) => unwrap(client.get<JobPage>('/jobs/local', { params })),
  remote: (params: JobSearchParams) => unwrap(client.get<JobPage>('/jobs/remote', { params })),
  search: (params: JobSearchParams & { scope?: 'LOCAL' | 'REMOTE' | 'ALL' }) =>
    unwrap(client.get<JobPage>('/jobs', { params })),
  getById: (id: string) => unwrap(client.get<JobResponse>(`/jobs/${id}`)),
  stats: () => unwrap(client.get<IngestionStatsResponse>('/jobs/stats')),
}
