// Mirrors the backend DTOs field-for-field. See the plan file for the source controllers
// this was surveyed from. String literal unions stand in for backend enums.

// ---- Auth / identity-service ------------------------------------------------------

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface UserSummary {
  id: string
  email: string
  fullName: string
  roles: string[]
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

// ---- Profile / profile-service ----------------------------------------------------

export type RemotePreference = 'ONSITE' | 'HYBRID' | 'REMOTE' | 'ANY'
export type Proficiency = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT'

export interface UpdateProfileRequest {
  fullName: string
  headline?: string | null
  summary?: string | null
  email?: string | null
  phone?: string | null
  locationCity?: string | null
  locationCountry?: string | null
  remotePreference?: RemotePreference | null
  linkedinUrl?: string | null
  githubUrl?: string | null
  portfolioUrl?: string | null
}

export interface EducationEntry {
  id: string
  institution: string
  degree?: string | null
  fieldOfStudy?: string | null
  startDate?: string | null
  endDate?: string | null
  grade?: string | null
  description?: string | null
}

export type EducationRequest = Omit<EducationEntry, 'id'>

export interface ExperienceEntry {
  id: string
  company: string
  title: string
  employmentType?: string | null
  locationCity?: string | null
  startDate: string
  endDate?: string | null
  current?: boolean | null
  description?: string | null
}

export type ExperienceRequest = Omit<ExperienceEntry, 'id'>

export interface ProfileSkillResponse {
  skillId: string
  name: string
  slug: string
  category: string
  proficiency: Proficiency
  yearsExperience?: number | null
  extractedFromResume: boolean
}

export interface SkillAssignment {
  slug: string
  proficiency: Proficiency
  yearsExperience?: number | null
}

export interface ProfileResponse {
  id: string
  userId: string
  fullName: string
  headline?: string | null
  summary?: string | null
  email?: string | null
  phone?: string | null
  locationCity?: string | null
  locationCountry?: string | null
  remotePreference?: RemotePreference | null
  yearsExperience?: number | null
  linkedinUrl?: string | null
  githubUrl?: string | null
  portfolioUrl?: string | null
  education: EducationEntry[]
  experience: ExperienceEntry[]
  skills: ProfileSkillResponse[]
  createdAt: string
  updatedAt: string
}

export interface SkillWithAliasesResponse {
  id: string
  name: string
  slug: string
  category: string
  aliases: string[]
}

export interface CoverLetterRequest {
  companyName?: string | null
  jobTitle?: string | null
  hiringManager?: string | null
  customBody?: string | null
}

// ---- Resumes / resume-service ------------------------------------------------------

export type ResumeStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'
export type SuggestionCategory =
  | 'WEAK_VERB'
  | 'UNQUANTIFIED_BULLET'
  | 'PASSIVE_VOICE'
  | 'BULLET_TOO_LONG'
  | 'FIRST_PERSON_PRONOUN'
  | 'MISSING_SECTION'

export interface ResumeSummaryResponse {
  id: string
  originalFilename: string
  status: ResumeStatus
  uploadedAt: string
  processedAt?: string | null
}

export interface ExtractedSkill {
  slug: string
  name: string
  category: string
  occurrenceCount: number
}

export interface AtsCheck {
  type: string
  severity: Severity
  message: string
}

export interface ResumeSuggestion {
  category: SuggestionCategory
  severity: Severity
  message: string
  evidence?: string | null
}

export interface ResumeDetailResponse {
  id: string
  originalFilename: string
  status: ResumeStatus
  errorMessage?: string | null
  inferredYearsExperience?: number | null
  extractedSkills: ExtractedSkill[]
  atsChecks: AtsCheck[]
  suggestions: ResumeSuggestion[]
  uploadedAt: string
  processedAt?: string | null
}

export interface ScoredTerm {
  term: string
  weight: number
}

export interface ResumeScoreResponse {
  resumeId: string
  jobId: string
  jobTitle: string
  jobCompany: string
  overallScore: number
  matchedTerms: ScoredTerm[]
  missingTerms: ScoredTerm[]
  actionableGaps: ScoredTerm[]
}

// ---- Jobs / job-service -------------------------------------------------------------

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP' | 'TEMPORARY' | 'OTHER'
export type JobSource = 'REMOTIVE' | 'ARBEITNOW' | 'REMOTEOK' | 'ADZUNA' | 'JSEARCH' | 'APIFY'
export type JobScope = 'LOCAL' | 'REMOTE' | 'ALL'

export interface JobResponse {
  id: string
  title: string
  company: string
  location?: string | null
  city?: string | null
  country?: string | null
  remote: boolean
  employmentType?: EmploymentType | null
  description?: string | null
  salaryMin?: number | null
  salaryMax?: number | null
  salaryCurrency?: string | null
  salaryRaw?: string | null
  applyUrl?: string | null
  tags: string[]
  postedAt?: string | null
  source: JobSource
  sourceAttribution?: string | null
}

export interface JobPage {
  content: JobResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export interface IngestionStatsResponse {
  totalListings: number
  distinctVacancies: number
  countsBySource: Record<string, number>
  circuitBreakers: Record<string, string>
}

// ---- Matches / matching-service ------------------------------------------------------

export interface ComponentScore {
  component: string
  score: number
  weight: number
  explanation: string
}

export interface CourseRef {
  provider: string
  title: string
  url: string
}

export interface MatchedSkill {
  slug: string
  name: string
  category: string
  rarityWeight: number
  proficiency: Proficiency
}

export interface MissingSkill {
  slug: string
  name: string
  category: string
  rarityWeight: number
  courses: CourseRef[]
}

export interface MatchJobSummary {
  id: string
  title: string
  company: string
  location?: string | null
  remote: boolean
  salaryMin?: number | null
  salaryMax?: number | null
  salaryCurrency?: string | null
  applyUrl?: string | null
  sourceAttribution?: string | null
}

export interface MatchResponse {
  job: MatchJobSummary
  overallScore: number
  breakdown: ComponentScore[]
  matchedSkills: MatchedSkill[]
  missingSkills: MissingSkill[]
}

export interface MatchPageResponse {
  count: number
  scope: JobScope
  matches: MatchResponse[]
}

export interface SkillGapEntry {
  slug: string
  name: string
  category: string
  demandCount: number
  demandPercentage: number
  rarityWeight: number
  courses: CourseRef[]
}

export interface SkillGapResponse {
  jobsAnalysed: number
  scope: JobScope
  gaps: SkillGapEntry[]
}

// ---- Tracker / tracker-service -------------------------------------------------------

export type ApplicationStatus = 'WISHLIST' | 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'
export type TransitionSource = 'MANUAL' | 'EMAIL_WEBHOOK' | 'INITIAL'

export interface StatusInfo {
  allowedTransitions: ApplicationStatus[]
  terminal: boolean
}

export type StatusMachine = Record<ApplicationStatus, StatusInfo>

export interface ApplicationEventResponse {
  fromStatus?: ApplicationStatus | null
  toStatus: ApplicationStatus
  source: TransitionSource
  note?: string | null
  createdAt: string
}

export interface ApplicationResponse {
  id: string
  jobId?: string | null
  jobTitle: string
  company: string
  applyUrl?: string | null
  location?: string | null
  status: ApplicationStatus
  allowedTransitions: ApplicationStatus[]
  notes?: string | null
  events: ApplicationEventResponse[]
  createdAt: string
  updatedAt: string
}

export interface BoardResponse {
  total: number
  columns: Record<ApplicationStatus, ApplicationResponse[]>
}

export interface CreateApplicationRequest {
  jobId?: string | null
  jobTitle: string
  company: string
  applyUrl?: string | null
  location?: string | null
  status?: ApplicationStatus | null
  notes?: string | null
}

export interface TransitionRequest {
  status: ApplicationStatus
  note?: string | null
}

export interface EmailWebhookRequest {
  from?: string | null
  subject?: string | null
  body?: string | null
  receivedAt?: string | null
}

export interface WebhookResultResponse {
  matched: boolean
  outcome: string
  applicationId?: string | null
  previousStatus?: ApplicationStatus | null
  newStatus?: ApplicationStatus | null
  matchedPhrase?: string | null
}

// ---- Errors --------------------------------------------------------------------------

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  timestamp: string
  correlationId?: string
  errors?: Record<string, string>
}
