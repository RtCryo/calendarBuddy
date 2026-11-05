import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { ProtectedRoute } from '../auth/ProtectedRoute'
import { clearSession, getToken, setSession } from '../auth/storage'
import { SignOutButton } from '../auth/SignOutButton'
import { LoginPage } from './LoginPage'
import { RegisterPage } from './RegisterPage'

afterEach(() => {
  cleanup()
  clearSession()
  vi.restoreAllMocks()
})

function login(initial = '/login') {
  render(<MemoryRouter initialEntries={[initial]}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/calendar" element={<h1>Calendar ready</h1>} />
    <Route path="/family" element={<h1>Family ready</h1>} />
  </Routes></MemoryRouter>)
}

function register() {
  render(<MemoryRouter><RegisterPage /></MemoryRouter>)
}

async function fillLogin() {
  await userEvent.type(screen.getByLabelText('Email address'), 'family@example.com')
  await userEvent.type(screen.getByLabelText('Password'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
}

async function fillRegistration() {
  await userEvent.type(screen.getByLabelText('Email address'), 'family@example.com')
  await userEvent.type(screen.getByLabelText('Password', { exact: true }), 'password123')
  await userEvent.type(screen.getByLabelText('Confirm password'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

function apiError(status?: number) {
  return new AxiosError('request failed', 'ERR_BAD_RESPONSE', undefined, undefined,
    status ? { data: {}, status, statusText: '', headers: {}, config: {} as InternalAxiosRequestConfig } : undefined)
}

describe('homeBuddy authentication', () => {
  it('redirects a visitor to login and returns to the requested page', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: { token: 'issued-token', email: 'family@example.com' } })
    render(<MemoryRouter initialEntries={['/family']}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}><Route path="/family" element={<h1>Family ready</h1>} /></Route>
    </Routes></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await fillLogin()
    expect(await screen.findByRole('heading', { name: 'Family ready' })).toBeInTheDocument()
    expect(getToken()).toBe('issued-token')
    expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'family@example.com', password: 'password123' })
  })

  it('shows invalid credentials without clearing the email', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(apiError(401))
    login()
    await fillLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.getByLabelText('Email address')).toHaveValue('family@example.com')
    expect(getToken()).toBeNull()
  })

  it('explains when the identity service is unavailable', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(apiError())
    login()
    await fillLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })

  it('registers the shared account and prepopulates sign-in', async () => {
    vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
    register()
    await fillRegistration()
    expect(await screen.findByRole('status')).toHaveTextContent('Your account is ready.')
    expect(screen.getByRole('link', { name: 'Continue to sign in' }))
      .toHaveAttribute('href', '/login?email=family%40example.com')
  })

  it('shows duplicate account and unavailable service errors', async () => {
    vi.spyOn(api, 'post').mockRejectedValueOnce(apiError(409)).mockRejectedValueOnce(apiError())
    register()
    await fillRegistration()
    expect(await screen.findByRole('alert')).toHaveTextContent('already exists')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable')
  })

  it('keeps the required fields in the registration form', async () => {
    const post = vi.spyOn(api, 'post')
    register()
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(post).not.toHaveBeenCalled()
  })

  it('signs out and protects the family page again', async () => {
    setSession('issued-token', 'family@example.com')
    render(<MemoryRouter initialEntries={['/family']}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}><Route path="/family" element={<><h1>Family ready</h1><SignOutButton /></>} /></Route>
    </Routes></MemoryRouter>)
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(getToken()).toBeNull()
  })
})
