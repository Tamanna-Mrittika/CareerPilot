import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse, ProblemDetail } from './types'

const REFRESH_TOKEN_KEY = 'careerpilot.refreshToken'
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  problem: ProblemDetail | undefined

  constructor(problem: ProblemDetail | undefined, fallbackMessage: string, status: number) {
    super(problem?.detail ?? fallbackMessage)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

// Access token lives in memory only -- never localStorage -- so a reload always requires
// a silent refresh. The refresh token is the only thing persisted client-side, matching
// what identity-service's plain-JSON (non-cookie) response actually gives us to work with.
let accessToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string | null) {
  if (token) localStorage.setItem(REFRESH_TOKEN_KEY, token)
  else localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/** Registered once by AuthContext; called when refresh itself fails so the app can redirect to /login. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export const client = axios.create({
  baseURL: `${BASE_URL}/api/v1`,
})

client.interceptors.request.use((config) => {
  if (accessToken && !config.headers.get('Authorization')) {
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

// Refresh-token rotation means a second concurrent refresh call would revoke the first
// call's whole token family (see tracker-service/identity-service docs). So every 401
// while a refresh is already in flight must wait on that SAME promise, never start its own.
let refreshInFlight: Promise<string> | null = null

async function performRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  const response = await axios.post<AuthResponse>(`${BASE_URL}/api/v1/auth/refresh`, { refreshToken })
  const { accessToken: newAccessToken, refreshToken: newRefreshToken } = response.data
  setAccessToken(newAccessToken)
  setRefreshToken(newRefreshToken)
  return newAccessToken
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined

    const isAuthEndpoint = original?.url?.includes('/auth/login') || original?.url?.includes('/auth/register')

    if (error.response?.status === 401 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true
      try {
        refreshInFlight ??= performRefresh().finally(() => {
          refreshInFlight = null
        })
        const newAccessToken = await refreshInFlight
        original.headers.set('Authorization', `Bearer ${newAccessToken}`)
        return client(original)
      } catch (refreshError) {
        setAccessToken(null)
        setRefreshToken(null)
        onUnauthorized?.()
        return Promise.reject(refreshError)
      }
    }

    const problem = error.response?.data
    throw new ApiError(problem, error.message, error.response?.status ?? 0)
  },
)

export async function unwrap<T>(promise: Promise<{ data: T }>): Promise<T> {
  const { data } = await promise
  return data
}

export type { AxiosRequestConfig }
