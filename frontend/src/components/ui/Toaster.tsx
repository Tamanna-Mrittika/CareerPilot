import * as ToastPrimitive from '@radix-ui/react-toast'
import { AlertTriangle, CheckCircle2, Info, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { dismissToast, useToasts, type ToastVariant } from './toast-store'

const icons: Record<ToastVariant, typeof Info> = {
  default: Info,
  success: CheckCircle2,
  danger: AlertTriangle,
}

const iconTone: Record<ToastVariant, string> = {
  default: 'text-brand-600',
  success: 'text-success',
  danger: 'text-danger',
}

export function Toaster() {
  const toasts = useToasts()

  return (
    <ToastPrimitive.Provider swipeDirection="right" duration={5000}>
      {toasts.map((t) => {
        const Icon = icons[t.variant]
        return (
          <ToastPrimitive.Root
            key={t.id}
            className={cn(
              'toast-anim flex items-start gap-3 rounded-card border border-border bg-surface p-4 shadow-popover',
              'data-[swipe=end]:animate-out',
            )}
            onOpenChange={(open) => {
              if (!open) dismissToast(t.id)
            }}
          >
            <Icon className={cn('mt-0.5 size-5 shrink-0', iconTone[t.variant])} />
            <div className="min-w-0 flex-1">
              <ToastPrimitive.Title className="text-sm font-semibold text-ink">{t.title}</ToastPrimitive.Title>
              {t.description && (
                <ToastPrimitive.Description className="mt-0.5 text-sm text-ink-muted">
                  {t.description}
                </ToastPrimitive.Description>
              )}
            </div>
            <ToastPrimitive.Close
              className="text-ink-faint transition-colors hover:text-ink"
              onClick={() => dismissToast(t.id)}
            >
              <X className="size-4" />
            </ToastPrimitive.Close>
          </ToastPrimitive.Root>
        )
      })}
      <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] m-0 flex w-full max-w-sm flex-col gap-2 p-6 outline-none" />
    </ToastPrimitive.Provider>
  )
}
