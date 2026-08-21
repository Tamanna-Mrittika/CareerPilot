import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { queryClient } from '@/lib/query-client'
import { AuthProvider } from '@/lib/auth/AuthContext'
import { AnonymousOnlyRoute, ProtectedRoute } from '@/lib/auth/ProtectedRoute'
import { AppShell } from '@/components/layout/AppShell'
import { Toaster } from '@/components/ui/Toaster'
import { LoginPage } from '@/pages/auth/LoginPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { ProfilePage } from '@/pages/ProfilePage'
import { ResumePage } from '@/pages/ResumePage'
import { JobsLocalPage } from '@/pages/jobs/JobsLocalPage'
import { JobsRemotePage } from '@/pages/jobs/JobsRemotePage'
import { SkillGapPage } from '@/pages/SkillGapPage'
import { TrackerPage } from '@/pages/TrackerPage'
import { SystemPage } from '@/pages/SystemPage'

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route element={<AnonymousOnlyRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>

            <Route element={<ProtectedRoute />}>
              <Route element={<AppShell />}>
                <Route path="/" element={<DashboardPage />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/resume" element={<ResumePage />} />
                <Route path="/jobs/local" element={<JobsLocalPage />} />
                <Route path="/jobs/remote" element={<JobsRemotePage />} />
                <Route path="/skill-gap" element={<SkillGapPage />} />
                <Route path="/tracker" element={<TrackerPage />} />
                <Route path="/system" element={<SystemPage />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
          <Toaster />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
