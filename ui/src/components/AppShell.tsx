import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { accountApi } from '../api/calendar'
import { setAccountZone } from '../calendar/dates'
import { getEmail } from '../auth/storage'
import { SignOutButton } from '../auth/SignOutButton'

const CalendarIcon = () => <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
  <rect x="3" y="5" width="18" height="16" rx="3" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>
const FamilyIcon = () => <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
  <circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15 14.5c3 0 6 1 6 5.5" /></svg>

const pageName = (path: string) => path.startsWith('/family') ? 'Family' : path.startsWith('/calendar') ? 'Calendar' : 'Page not found'

export function AppShell() {
  const { pathname } = useLocation()
  const email = getEmail()
  const link = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`
  // Pages convert dates in the account time zone, so they render once the API has reported it.
  const [zoneReady, setZoneReady] = useState(false)
  useEffect(() => {
    let active = true
    accountApi.get().then(account => { if (active) setAccountZone(account.timeZone) })
      .catch(() => undefined).finally(() => { if (active) setZoneReady(true) })
    return () => { active = false }
  }, [])
  return <div className="app-shell">
    <aside className="sidebar">
      <NavLink to="/calendar" className="brand" aria-label="calendarBuddy home">
        <span className="brand-mark" aria-hidden="true"><CalendarIcon /></span>
        <span className="brand-name">calendar<span>Buddy</span></span></NavLink>
      <p className="nav-caption">Workspace</p>
      <nav className="workspace-nav" aria-label="Main navigation">
        <NavLink to="/calendar" className={link}><CalendarIcon /><span>Calendar</span></NavLink>
        <NavLink to="/family" className={link}><FamilyIcon /><span>Family</span></NavLink>
      </nav>
      <div className="sidebar-foot">A shared homeBuddy account for your family.</div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <nav aria-label="Breadcrumb"><ol className="breadcrumb"><li>homeBuddy</li><li aria-hidden="true">/</li><li>calendarBuddy</li><li aria-hidden="true">/</li>
          <li aria-current="page">{pageName(pathname)}</li></ol></nav>
        <div className="profile">{email && <span className="profile-email" title={email}>{email}</span>}<SignOutButton /></div>
      </header>
      <main className="main-content">{zoneReady ? <Outlet /> : <p role="status">Loading your workspace…</p>}</main>
      <footer className="workspace-footer">calendarBuddy · Make room for what matters.</footer>
    </div>
  </div>
}

export function NotFound() {
  return <div className="surface empty-state"><h1>Page not found</h1><p>This page does not exist.</p>
    <Link className="button button-primary" to="/calendar">Go to Calendar</Link></div>
}
