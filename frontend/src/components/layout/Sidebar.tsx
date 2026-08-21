import {
  Activity,
  Building2,
  Compass,
  FileText,
  KanbanSquare,
  LayoutDashboard,
  TrendingUp,
  User,
  X,
} from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { cn } from '@/lib/utils'

const navGroups: { label: string; items: { to: string; label: string; icon: typeof LayoutDashboard }[] }[] = [
  {
    label: 'Overview',
    items: [{ to: '/', label: 'Dashboard', icon: LayoutDashboard }],
  },
  {
    label: 'Discover',
    items: [
      { to: '/jobs/local', label: 'Jobs in Dhaka', icon: Building2 },
      { to: '/jobs/remote', label: 'Remote jobs', icon: Compass },
      { to: '/skill-gap', label: 'Skill gap', icon: TrendingUp },
    ],
  },
  {
    label: 'You',
    items: [
      { to: '/resume', label: 'Resume', icon: FileText },
      { to: '/tracker', label: 'Tracker', icon: KanbanSquare },
      { to: '/profile', label: 'Profile', icon: User },
    ],
  },
  {
    label: 'Platform',
    items: [{ to: '/system', label: 'System health', icon: Activity }],
  },
]

function SidebarInner({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <>
      <div className="flex h-16 items-center gap-2 px-6">
        <div className="flex size-8 items-center justify-center rounded-[0.6rem] bg-brand-600 text-sm font-bold text-white">
          C
        </div>
        <span className="text-[15px] font-semibold tracking-tight text-ink">CareerPilot</span>
      </div>

      <nav className="flex-1 space-y-6 overflow-y-auto px-3 py-4">
        {navGroups.map((group) => (
          <div key={group.label}>
            <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-ink-faint">
              {group.label}
            </p>
            <div className="space-y-0.5">
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2.5 rounded-control px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-brand-50 text-brand-700'
                        : 'text-ink-muted hover:bg-surface-sunken hover:text-ink',
                    )
                  }
                >
                  <item.icon className="size-4" />
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>
    </>
  )
}

export function Sidebar() {
  return (
    <aside className="hidden w-64 shrink-0 flex-col border-r border-border bg-surface md:flex">
      <SidebarInner />
    </aside>
  )
}

/** Slide-in drawer shown below the md breakpoint, where the static Sidebar is hidden. */
export function MobileSidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <div className={cn('fixed inset-0 z-40 md:hidden', open ? 'pointer-events-auto' : 'pointer-events-none')}>
      <div
        className={cn(
          'absolute inset-0 bg-ink/40 transition-opacity',
          open ? 'opacity-100' : 'opacity-0',
        )}
        onClick={onClose}
      />
      <aside
        className={cn(
          'absolute inset-y-0 left-0 flex w-72 max-w-[80vw] flex-col bg-surface shadow-popover transition-transform duration-200',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <button onClick={onClose} className="absolute right-3 top-4 rounded-full p-1.5 text-ink-muted hover:bg-surface-sunken">
          <X className="size-4" />
        </button>
        <SidebarInner onNavigate={onClose} />
      </aside>
    </div>
  )
}
