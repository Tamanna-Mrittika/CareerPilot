import { Construction } from 'lucide-react'
import { EmptyState } from '@/components/ui/EmptyState'

export function ComingSoon({ label }: { label: string }) {
  return <EmptyState icon={Construction} title={`${label} is being built`} description="Check back shortly." />
}
