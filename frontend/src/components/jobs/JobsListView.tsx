import { useQuery } from '@tanstack/react-query'
import { Briefcase, Search } from 'lucide-react'
import { useState } from 'react'
import { matchesApi } from '@/lib/api/matches'
import type { MatchResponse } from '@/lib/api/types'
import { useDebounce } from '@/lib/use-debounce'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { Input } from '@/components/ui/Input'
import { PageSpinner } from '@/components/ui/Spinner'
import { Skeleton } from '@/components/ui/Skeleton'
import { JobCard } from './JobCard'
import { JobDetailDialog } from './JobDetailDialog'

const PAGE_SIZE = 15

export function JobsListView({ scope }: { scope: 'local' | 'remote' }) {
  const [query, setQuery] = useState('')
  const [city, setCity] = useState('')
  const [limit, setLimit] = useState(PAGE_SIZE)
  const [selected, setSelected] = useState<MatchResponse | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const debouncedQuery = useDebounce(query, 350)
  const debouncedCity = useDebounce(city, 350)

  const matchesQuery = useQuery({
    queryKey: ['matches', scope, debouncedQuery, debouncedCity, limit],
    queryFn: () =>
      scope === 'local'
        ? matchesApi.local({ q: debouncedQuery || undefined, city: debouncedCity || undefined, limit })
        : matchesApi.remote({ q: debouncedQuery || undefined, limit }),
  })

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-faint" />
          <Input
            placeholder="Search by title, company, or keyword..."
            className="pl-9"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        {scope === 'local' && (
          <Input
            placeholder="City (default: Dhaka)"
            className="sm:w-52"
            value={city}
            onChange={(e) => setCity(e.target.value)}
          />
        )}
      </div>

      {matchesQuery.isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      ) : !matchesQuery.data || matchesQuery.data.matches.length === 0 ? (
        <EmptyState
          icon={Briefcase}
          title="No matching postings found"
          description="Try a different search term, or check back after the next ingestion cycle."
        />
      ) : (
        <>
          <p className="text-sm text-ink-muted">
            {matchesQuery.data.count} {scope === 'local' ? 'local' : 'remote'} posting
            {matchesQuery.data.count === 1 ? '' : 's'} ranked by fit
          </p>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {matchesQuery.data.matches.map((match) => (
              <JobCard
                key={match.job.id}
                match={match}
                onClick={() => {
                  setSelected(match)
                  setDialogOpen(true)
                }}
              />
            ))}
          </div>
          {matchesQuery.data.matches.length >= limit && (
            <div className="flex justify-center pt-2">
              <Button variant="outline" onClick={() => setLimit((l) => l + PAGE_SIZE)}>
                Load more
              </Button>
            </div>
          )}
          {matchesQuery.isFetching && <PageSpinner />}
        </>
      )}

      <JobDetailDialog match={selected} open={dialogOpen} onOpenChange={setDialogOpen} />
    </div>
  )
}
