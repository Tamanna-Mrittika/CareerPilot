import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { MobileSidebar, Sidebar } from './Sidebar'
import { Topbar } from './Topbar'

const PAGE_TITLES: { match: (path: string) => boolean; title: string }[] = [
  { match: (p) => p === '/', title: 'Dashboard' },
  { match: (p) => p.startsWith('/jobs/local'), title: 'Jobs in Dhaka' },
  { match: (p) => p.startsWith('/jobs/remote'), title: 'Remote jobs' },
  { match: (p) => p.startsWith('/skill-gap'), title: 'Skill gap' },
  { match: (p) => p.startsWith('/resume'), title: 'Resume' },
  { match: (p) => p.startsWith('/tracker'), title: 'Application tracker' },
  { match: (p) => p.startsWith('/profile'), title: 'Profile' },
  { match: (p) => p.startsWith('/system'), title: 'System health' },
]

export function AppShell() {
  const location = useLocation()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const title = PAGE_TITLES.find((entry) => entry.match(location.pathname))?.title ?? 'CareerPilot'

  return (
    <div className="flex h-screen overflow-hidden bg-app-bg">
      <Sidebar />
      <MobileSidebar open={mobileNavOpen} onClose={() => setMobileNavOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar title={title} onMenuClick={() => setMobileNavOpen(true)} />
        <main className="flex-1 overflow-y-auto px-4 py-6 sm:px-6 md:px-8 md:py-8">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
