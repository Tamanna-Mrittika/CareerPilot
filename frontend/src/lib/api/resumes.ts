import { client, unwrap } from './client'
import type { ResumeDetailResponse, ResumeScoreResponse, ResumeSummaryResponse } from './types'

export const resumesApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap(
      client.post<ResumeSummaryResponse>('/resumes', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    )
  },
  listMine: () => unwrap(client.get<ResumeSummaryResponse[]>('/resumes/me')),
  getById: (id: string) => unwrap(client.get<ResumeDetailResponse>(`/resumes/${id}`)),
  scoreAgainstJob: (id: string, jobId: string) =>
    unwrap(client.get<ResumeScoreResponse>(`/resumes/${id}/score`, { params: { jobId } })),
}
