import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '@/lib/auth/AuthContext'
import { ApiError } from '@/lib/api/client'
import { Button } from '@/components/ui/Button'
import { FieldError, Input } from '@/components/ui/Input'
import { Label } from '@/components/ui/Label'
import { AuthLayout } from './AuthLayout'

const schema = z.object({
  fullName: z.string().min(1, 'Full name is required'),
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(12, 'Password must be at least 12 characters'),
})

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { register: registerUser } = useAuth()
  const navigate = useNavigate()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = async (values: FormValues) => {
    try {
      await registerUser(values)
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.problem?.errors) {
        for (const [field, message] of Object.entries(error.problem.errors)) {
          setError(field as keyof FormValues, { message })
        }
        return
      }
      const message = error instanceof ApiError ? error.message : 'Unable to create your account'
      setError('root', { message })
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start matching your resume to real openings">
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4">
        <div>
          <Label htmlFor="fullName">Full name</Label>
          <Input id="fullName" autoComplete="name" placeholder="Jane Doe" {...register('fullName')} />
          <FieldError>{errors.fullName?.message}</FieldError>
        </div>
        <div>
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" autoComplete="email" placeholder="you@example.com" {...register('email')} />
          <FieldError>{errors.email?.message}</FieldError>
        </div>
        <div>
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="new-password" {...register('password')} />
          {errors.password?.message ? (
            <FieldError>{errors.password.message}</FieldError>
          ) : (
            <p className="mt-1.5 text-xs text-ink-faint">At least 12 characters</p>
          )}
        </div>
        {errors.root?.message && (
          <p className="rounded-control bg-danger-bg px-3 py-2 text-sm text-danger">{errors.root.message}</p>
        )}
        <Button type="submit" className="w-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
      <p className="mt-5 text-center text-sm text-ink-muted">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
