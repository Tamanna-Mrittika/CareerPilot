import { client, unwrap } from './client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserSummary } from './types'

export const authApi = {
  register: (body: RegisterRequest) => unwrap(client.post<AuthResponse>('/auth/register', body)),
  login: (body: LoginRequest) => unwrap(client.post<AuthResponse>('/auth/login', body)),
  refresh: (refreshToken: string) => unwrap(client.post<AuthResponse>('/auth/refresh', { refreshToken })),
  logout: (refreshToken: string) => client.post('/auth/logout', { refreshToken }),
  me: () => unwrap(client.get<UserSummary>('/auth/me')),
}
