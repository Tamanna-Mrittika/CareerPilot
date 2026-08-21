import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { PageSpinner } from '@/components/ui/Spinner'

export function ProtectedRoute() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <PageSpinner />
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}

export function AnonymousOnlyRoute() {
  const { status } = useAuth()

  if (status === 'loading') return <PageSpinner />
  if (status === 'authenticated') return <Navigate to="/" replace />
  return <Outlet />
}
