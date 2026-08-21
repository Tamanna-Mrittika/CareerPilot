import { useQuery } from '@tanstack/react-query'
import { profileApi } from '@/lib/api/profile'
import { BasicsForm } from '@/components/profile/BasicsForm'
import { EducationTab } from '@/components/profile/EducationTab'
import { ExperienceTab } from '@/components/profile/ExperienceTab'
import { SkillsTab } from '@/components/profile/SkillsTab'
import { CoverLetterDialog } from '@/components/profile/CoverLetterDialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/Tabs'
import { PageSpinner } from '@/components/ui/Spinner'
import { Badge } from '@/components/ui/Badge'

export function ProfilePage() {
  const profileQuery = useQuery({ queryKey: ['profile', 'me'], queryFn: profileApi.getMe })

  if (profileQuery.isLoading || !profileQuery.data) return <PageSpinner />

  const profile = profileQuery.data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold text-ink">Your profile</h2>
          <div className="mt-1.5 flex items-center gap-2">
            {profile.yearsExperience != null && (
              <Badge tone="brand">{profile.yearsExperience} yrs experience</Badge>
            )}
            <Badge tone="neutral">{profile.skills.length} skills</Badge>
          </div>
        </div>
        <CoverLetterDialog />
      </div>

      <Tabs defaultValue="basics">
        <TabsList>
          <TabsTrigger value="basics">Basics</TabsTrigger>
          <TabsTrigger value="education">Education</TabsTrigger>
          <TabsTrigger value="experience">Experience</TabsTrigger>
          <TabsTrigger value="skills">Skills</TabsTrigger>
        </TabsList>

        <TabsContent value="basics">
          <BasicsForm profile={profile} />
        </TabsContent>
        <TabsContent value="education">
          <EducationTab profile={profile} />
        </TabsContent>
        <TabsContent value="experience">
          <ExperienceTab profile={profile} />
        </TabsContent>
        <TabsContent value="skills">
          <SkillsTab profile={profile} />
        </TabsContent>
      </Tabs>
    </div>
  )
}
