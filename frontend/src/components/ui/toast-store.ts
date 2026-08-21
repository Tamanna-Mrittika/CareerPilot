import { useSyncExternalStore } from 'react'

export type ToastVariant = 'default' | 'success' | 'danger'

export interface ToastItem {
  id: string
  title: string
  description?: string
  variant: ToastVariant
}

let toasts: ToastItem[] = []
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((l) => l())
}

export function useToasts() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => toasts,
  )
}

export function dismissToast(id: string) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

export function toast(input: { title: string; description?: string; variant?: ToastVariant }) {
  const id = crypto.randomUUID()
  toasts = [...toasts, { id, variant: input.variant ?? 'default', title: input.title, description: input.description }]
  emit()
  setTimeout(() => dismissToast(id), 5000)
  return id
}

export function toastFromError(error: unknown, fallbackTitle = 'Something went wrong') {
  const message = error instanceof Error ? error.message : String(error)
  toast({ title: fallbackTitle, description: message, variant: 'danger' })
}
