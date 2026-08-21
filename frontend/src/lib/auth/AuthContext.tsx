import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '@/lib/api/auth'
import { getRefreshToken, setAccessToken, setRefreshToken, setUnauthorizedHandler } from '@/lib/api/client'
import type { LoginRequest, RegisterRequest, UserSummary } from '@/lib/api/types'
import { queryClient } from '@/lib/query-client'

interface AuthContextValue {
  user: UserSummary | null
  status: 'loading' | 'authenticated' | 'anonymous'
  isAdmin: boolean
  login: (body: LoginRequest) => Promise<void>
  register: (body: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [status, setStatus] = useState<'loading' | 'authenticated' | 'anonymous'>('loading')

  const reset = useCallback(() => {
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
    setStatus('anonymous')
    queryClient.clear()
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(reset)
  }, [reset])

  // On first load there is no access token in memory (by design -- see client.ts), so a
  // stored refresh token is the only way to silently resume a session across a page reload.
  useEffect(() => {
    const refreshToken = getRefreshToken()
    if (!refreshToken) {
      setStatus('anonymous')
      return
    }
    authApi
      .refresh(refreshToken)
      .then((res) => {
        setAccessToken(res.accessToken)
        setRefreshToken(res.refreshToken)
        setUser(res.user)
        setStatus('authenticated')
      })
      .catch(() => {
        reset()
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(async (body: LoginRequest) => {
    const res = await authApi.login(body)
    setAccessToken(res.accessToken)
    setRefreshToken(res.refreshToken)
    setUser(res.user)
    setStatus('authenticated')
  }, [])

  const register = useCallback(async (body: RegisterRequest) => {
    const res = await authApi.register(body)
    setAccessToken(res.accessToken)
    setRefreshToken(res.refreshToken)
    setUser(res.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // Logout is best-effort -- even if the network call fails, clear local state.
    }
    reset()
  }, [reset])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      isAdmin: user?.roles.includes('ADMIN') ?? false,
      login,
      register,
      logout,
    }),
    [user, status, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
