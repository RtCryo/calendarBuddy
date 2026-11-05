import { useState } from 'react'
import type { FormEvent } from 'react'
import axios from 'axios'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { AuthLayout } from '../components/AuthLayout'

export function RegisterPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pending, setPending] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (password !== confirm) {
      setError('Your passwords do not match.')
      return
    }
    setPending(true)
    try {
      await api.post('/auth/register', { email, password })
      setSuccess(true)
    } catch (failure) {
      if (axios.isAxiosError(failure) && failure.response?.status === 409) {
        setError('A homeBuddy account with this email already exists. Sign in instead.')
      } else if (axios.isAxiosError(failure) && !failure.response) {
        setError('Sign-in is temporarily unavailable. Please try again.')
      } else {
        setError('We could not create your account. Please try again.')
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <AuthLayout eyebrow="GET STARTED" title="Create your account" description="Create one homeBuddy account for your family, shared with finanzBuddy." footer={<>Already have an account? <Link to="/login">Sign in</Link></>}>
      {success ? <div className="notice notice-success" role="status"><strong>Your account is ready.</strong><Link to={`/login?email=${encodeURIComponent(email)}`}>Continue to sign in</Link></div> :
        <form className="form-stack" onSubmit={submit}>
          {error && <div className="notice notice-error" role="alert">{error} {error.includes('already exists') && <Link to={`/login?email=${encodeURIComponent(email)}`}>Sign in</Link>}</div>}
          <label className="field"><span>Email address</span><input type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} /></label>
          <label className="field"><span>Password</span><input type="password" autoComplete="new-password" minLength={6} maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} /></label>
          <label className="field"><span>Confirm password</span><input type="password" autoComplete="new-password" required value={confirm} onChange={event => setConfirm(event.target.value)} /></label>
          <button className="button button-primary auth-submit" type="submit" disabled={pending}>{pending ? 'Creating account…' : 'Create account'}</button>
        </form>}
    </AuthLayout>
  )
}
