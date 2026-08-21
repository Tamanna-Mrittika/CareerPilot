import { LogOut, Menu } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth/AuthContext'
import { initials } from '@/lib/utils'
import { Button } from '@/components/ui/Button'

export function Topbar({ title, onMenuClick }: { title: string; onMenuClick: () => void }) {
  const { user, logout, isAdmin } = useAuth()
  const navigate = useNavigate()

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-border bg-surface px-4 sm:px-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" className="md:hidden" onClick={onMenuClick}>
          <Menu className="size-5" />
        </Button>
        <h1 className="text-lg font-semibold text-ink">{title}</h1>
      </div>

      <div className="flex items-center gap-3">
        {isAdmin && (
          <span className="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700">Admin</span>
        )}
        <div className="flex items-center gap-2.5 rounded-full border border-border py-1 pl-1 pr-3">
          <div className="flex size-7 items-center justify-center rounded-full bg-brand-600 text-xs font-semibold text-white">
            {initials(user?.fullName)}
          </div>
          <span className="hidden text-sm font-medium text-ink sm:inline">{user?.fullName}</span>
        </div>
        <Button
          variant="ghost"
          size="icon"
          title="Log out"
          onClick={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          <LogOut className="size-4" />
        </Button>
      </div>
    </header>
  )
}
