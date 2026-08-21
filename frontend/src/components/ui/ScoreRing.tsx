import { cn, scoreBand } from '@/lib/utils'

const bandColor = {
  low: 'var(--color-score-low)',
  mid: 'var(--color-score-mid)',
  high: 'var(--color-score-high)',
} as const

const sizes = {
  sm: { box: 36, stroke: 3.5, text: 'text-[10px]' },
  md: { box: 56, stroke: 4.5, text: 'text-sm' },
  lg: { box: 96, stroke: 6, text: 'text-2xl' },
} as const

interface ScoreRingProps {
  /** 0-100, or null/undefined when the score is inapplicable (renders "N/A", never as 0). */
  score: number | null | undefined
  size?: keyof typeof sizes
  className?: string
}

export function ScoreRing({ score, size = 'md', className }: ScoreRingProps) {
  const { box, stroke, text } = sizes[size]
  const radius = (box - stroke) / 2
  const circumference = 2 * Math.PI * radius
  const clamped = score == null ? null : Math.max(0, Math.min(100, score))
  const offset = clamped == null ? circumference : circumference - (clamped / 100) * circumference
  const color = clamped == null ? 'var(--color-border-strong)' : bandColor[scoreBand(clamped)]

  return (
    <div className={cn('relative inline-flex shrink-0 items-center justify-center', className)} style={{ width: box, height: box }}>
      <svg width={box} height={box} className="-rotate-90">
        <circle cx={box / 2} cy={box / 2} r={radius} fill="none" stroke="var(--color-border)" strokeWidth={stroke} />
        {clamped != null && (
          <circle
            cx={box / 2}
            cy={box / 2}
            r={radius}
            fill="none"
            stroke={color}
            strokeWidth={stroke}
            strokeDasharray={circumference}
            strokeDashoffset={offset}
            strokeLinecap="round"
            className="transition-[stroke-dashoffset] duration-500 ease-out"
          />
        )}
      </svg>
      <span className={cn('absolute font-semibold tabular-nums text-ink', text)}>
        {clamped == null ? 'N/A' : Math.round(clamped)}
      </span>
    </div>
  )
}
