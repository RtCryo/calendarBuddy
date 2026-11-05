import { useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { returnPathFromLocation } from '../auth/returnPath'
import { setSession } from '../auth/storage'
import { AuthLayout } from '../components/AuthLayout'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') ?? '')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setPending(true)
    try {
      const response = await api.post<{ token: string, email: string }>('/auth/login', { email, password })
      if (!response.data.token) throw new Error('Missing access token')
      setSession(response.data.token, response.data.email || email)
      navigate(returnPathFromLocation(location.state, location.search), { replace: true })
    } catch (failure) {
      if (axios.isAxiosError(failure) && failure.response?.status === 401) {
        setError('Invalid email or password.')
      } else if (axios.isAxiosError(failure) && !failure.response) {
        setError('Sign-in is temporarily unavailable. Please try again.')
      } else {
        setError('We could not sign you in. Please try again.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthLayout eyebrow="WELCOME BACK" title="Sign in" description="Use your family's homeBuddy account, shared with finanzBuddy." footer={<>New to homeBuddy? <Link to="/register">Create account</Link></>}>
      <form className="form-stack" onSubmit={submit}>
        {error && <div className="notice notice-error" role="alert">{error}</div>}
        <label className="field"><span>Email address</span><input type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} /></label>
        <label className="field"><span>Password</span><input type="password" autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} /></label>
        <button className="button button-primary auth-submit" type="submit" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </AuthLayout>
  )
}
