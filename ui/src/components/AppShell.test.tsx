import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import { accountApi, calendarApi, familyApi } from '../api/calendar'
import { AppRoutes } from '../App'
import { clearSession, setSession } from '../auth/storage'

afterEach(() => { cleanup(); clearSession(); vi.restoreAllMocks() })

function openAt(path: string) {
  setSession('token', 'family@example.com')
  vi.spyOn(accountApi, 'get').mockResolvedValue({ email: 'family@example.com', timeZone: 'Europe/Berlin' })
  vi.spyOn(familyApi, 'list').mockResolvedValue([])
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  render(<MemoryRouter initialEntries={[path]}><AppRoutes /></MemoryRouter>)
}

it('shows the brand, Calendar and Family links, the active page, email, and sign-out', async () => {
  openAt('/family')
  const nav = screen.getByRole('navigation', { name: 'Main navigation' })
  expect(screen.getByRole('link', { name: 'calendarBuddy home' })).toHaveTextContent('calendarBuddy')
  expect(within(nav).getByRole('link', { name: 'Family' })).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByRole('link', { name: 'Calendar' })).not.toHaveAttribute('aria-current')
  expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent('homeBuddy/calendarBuddy/Family')
  expect(screen.getByText('family@example.com')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  expect(screen.getByRole('contentinfo')).toHaveTextContent('calendarBuddy')
})

it('opens the calendar from the workspace root', async () => {
  openAt('/')
  expect(await screen.findByRole('heading', { name: 'Family calendar' })).toBeInTheDocument()
  expect(within(screen.getByRole('navigation', { name: 'Main navigation' })).getByRole('link', { name: 'Calendar' })).toHaveAttribute('aria-current', 'page')
})

it('shows a not-found state with a link to the calendar for unknown routes', async () => {
  openAt('/nope')
  expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Go to Calendar' }))
  expect(await screen.findByRole('heading', { name: 'Family calendar' })).toBeInTheDocument()
})

it('signs out to the login page', async () => {
  openAt('/calendar')
  await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByRole('heading', { name: /sign in|welcome/i })).toBeInTheDocument()
})
