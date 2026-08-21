import { client, unwrap } from './client'
import type {
  CoverLetterRequest,
  EducationEntry,
  EducationRequest,
  ExperienceEntry,
  ExperienceRequest,
  ProfileResponse,
  ProfileSkillResponse,
  SkillAssignment,
  SkillWithAliasesResponse,
  UpdateProfileRequest,
} from './types'

export const profileApi = {
  getMe: () => unwrap(client.get<ProfileResponse>('/profiles/me')),
  updateMe: (body: UpdateProfileRequest) => unwrap(client.put<ProfileResponse>('/profiles/me', body)),

  addEducation: (body: EducationRequest) => unwrap(client.post<EducationEntry>('/profiles/me/education', body)),
  removeEducation: (id: string) => client.delete(`/profiles/me/education/${id}`),

  addExperience: (body: ExperienceRequest) => unwrap(client.post<ExperienceEntry>('/profiles/me/experience', body)),
  removeExperience: (id: string) => client.delete(`/profiles/me/experience/${id}`),

  getSkills: () => unwrap(client.get<ProfileSkillResponse[]>('/profiles/me/skills')),
  replaceSkills: (skills: SkillAssignment[]) =>
    unwrap(client.put<ProfileSkillResponse[]>('/profiles/me/skills', { skills })),

  taxonomy: () => unwrap(client.get<SkillWithAliasesResponse[]>('/skills/taxonomy')),

  generateCoverLetter: (body: CoverLetterRequest) =>
    client.post('/cover-letters', body, { responseType: 'blob' }),
}
