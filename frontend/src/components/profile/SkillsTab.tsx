import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, Sparkles, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { profileApi } from '@/lib/api/profile'
import type { Proficiency, ProfileResponse, SkillAssignment, SkillWithAliasesResponse } from '@/lib/api/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/Select'
import { PageSpinner } from '@/components/ui/Spinner'
import { toast, toastFromError } from '@/components/ui/toast-store'
import { cn } from '@/lib/utils'

const PROFICIENCIES: Proficiency[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']

interface Draft {
  slug: string
  name: string
  category: string
  proficiency: Proficiency
  yearsExperience: number | null
}

export function SkillsTab({ profile }: { profile: ProfileResponse }) {
  const queryClient = useQueryClient()
  const taxonomyQuery = useQuery({ queryKey: ['skills', 'taxonomy'], queryFn: profileApi.taxonomy })

  const [drafts, setDrafts] = useState<Draft[]>(() =>
    profile.skills.map((s) => ({
      slug: s.slug,
      name: s.name,
      category: s.category,
      proficiency: s.proficiency,
      yearsExperience: s.yearsExperience ?? null,
    })),
  )
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<string | null>(null)

  const categories = useMemo(() => {
    if (!taxonomyQuery.data) return []
    return Array.from(new Set(taxonomyQuery.data.map((s) => s.category))).sort()
  }, [taxonomyQuery.data])

  const selectedSlugs = useMemo(() => new Set(drafts.map((d) => d.slug)), [drafts])

  const suggestions = useMemo(() => {
    if (!taxonomyQuery.data) return []
    const q = query.trim().toLowerCase()
    return taxonomyQuery.data
      .filter((s) => !selectedSlugs.has(s.slug))
      .filter((s) => !category || s.category === category)
      .filter((s) => !q || s.name.toLowerCase().includes(q) || s.aliases.some((a) => a.toLowerCase().includes(q)))
      .slice(0, 24)
  }, [taxonomyQuery.data, query, category, selectedSlugs])

  const addSkill = (entry: SkillWithAliasesResponse) => {
    setDrafts((prev) => [
      ...prev,
      { slug: entry.slug, name: entry.name, category: entry.category, proficiency: 'INTERMEDIATE', yearsExperience: null },
    ])
  }

  const removeSkill = (slug: string) => setDrafts((prev) => prev.filter((d) => d.slug !== slug))

  const updateDraft = (slug: string, patch: Partial<Draft>) =>
    setDrafts((prev) => prev.map((d) => (d.slug === slug ? { ...d, ...patch } : d)))

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload: SkillAssignment[] = drafts.map((d) => ({
        slug: d.slug,
        proficiency: d.proficiency,
        yearsExperience: d.yearsExperience,
      }))
      return profileApi.replaceSkills(payload)
    },
    onSuccess: (skills) => {
      queryClient.setQueryData<ProfileResponse>(['profile', 'me'], (prev) => (prev ? { ...prev, skills } : prev))
      toast({ title: 'Skills saved', variant: 'success' })
    },
    onError: (error) => toastFromError(error, 'Could not save skills'),
  })

  if (taxonomyQuery.isLoading) return <PageSpinner />

  const dirty =
    drafts.length !== profile.skills.length ||
    drafts.some((d) => {
      const original = profile.skills.find((s) => s.slug === d.slug)
      return !original || original.proficiency !== d.proficiency || (original.yearsExperience ?? null) !== d.yearsExperience
    })

  return (
    <div className="space-y-6">
      <div>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-faint" />
          <Input
            placeholder="Search skills (try 'k8s', 'react', 'postgres'...)"
            className="pl-9"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <button
            onClick={() => setCategory(null)}
            className={cn(
              'rounded-full px-3 py-1 text-xs font-medium transition-colors',
              category === null ? 'bg-brand-600 text-white' : 'bg-surface-sunken text-ink-muted hover:bg-border',
            )}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c}
              onClick={() => setCategory(c)}
              className={cn(
                'rounded-full px-3 py-1 text-xs font-medium transition-colors',
                category === c ? 'bg-brand-600 text-white' : 'bg-surface-sunken text-ink-muted hover:bg-border',
              )}
            >
              {c}
            </button>
          ))}
        </div>

        {suggestions.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {suggestions.map((s) => (
              <button
                key={s.slug}
                onClick={() => addSkill(s)}
                className="rounded-full border border-border bg-surface px-3 py-1.5 text-sm text-ink transition-colors hover:border-brand-500 hover:bg-brand-50"
              >
                + {s.name}
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        <p className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-ink">
          <Sparkles className="size-4 text-brand-600" />
          Your skills ({drafts.length})
        </p>
        {drafts.length === 0 ? (
          <p className="rounded-control border border-dashed border-border-strong px-4 py-6 text-center text-sm text-ink-muted">
            Search above and click a skill to add it.
          </p>
        ) : (
          <div className="space-y-2">
            {drafts.map((d) => (
              <div
                key={d.slug}
                className="flex flex-wrap items-center gap-3 rounded-control border border-border px-3 py-2.5"
              >
                <div className="min-w-32 flex-1">
                  <p className="text-sm font-medium text-ink">{d.name}</p>
                  <Badge tone="neutral" className="mt-0.5">
                    {d.category}
                  </Badge>
                </div>
                <Select value={d.proficiency} onValueChange={(v) => updateDraft(d.slug, { proficiency: v as Proficiency })}>
                  <SelectTrigger className="w-40">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {PROFICIENCIES.map((p) => (
                      <SelectItem key={p} value={p}>
                        {p.charAt(0) + p.slice(1).toLowerCase()}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Input
                  type="number"
                  min={0}
                  max={50}
                  placeholder="Years"
                  className="w-24"
                  value={d.yearsExperience ?? ''}
                  onChange={(e) =>
                    updateDraft(d.slug, { yearsExperience: e.target.value === '' ? null : Number(e.target.value) })
                  }
                />
                <button
                  onClick={() => removeSkill(d.slug)}
                  className="rounded-full p-1.5 text-ink-faint transition-colors hover:bg-danger-bg hover:text-danger"
                >
                  <X className="size-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="flex justify-end">
        <Button onClick={() => saveMutation.mutate()} loading={saveMutation.isPending} disabled={!dirty}>
          Save changes
        </Button>
      </div>
    </div>
  )
}
