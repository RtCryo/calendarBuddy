import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

type AuthLayoutProps = {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
  footer: ReactNode
}

export function AuthLayout({ eyebrow, title, description, children, footer }: AuthLayoutProps) {
  return (
    <main className="auth-page">
      <div className="auth-card">
        <section className="auth-form-panel">
          <Link className="brand auth-brand" to="/login" aria-label="calendarBuddy home">
            <span className="brand-mark" aria-hidden="true">▣</span>
            <span className="brand-name">calendar<span>Buddy</span></span>
          </Link>
          <div className="auth-form-content">
            <p className="auth-eyebrow">{eyebrow}</p>
            <h1>{title}</h1>
            <p className="auth-description">{description}</p>
            {children}
            <div className="auth-footer">{footer}</div>
          </div>
          <p className="auth-privacy">Your family's calendar is private to your homeBuddy account.</p>
        </section>
        <aside className="auth-story" aria-label="About calendarBuddy">
          <div className="story-orbit" aria-hidden="true"><div className="story-card">Family plans, together</div></div>
          <div className="story-copy"><h2>Make room for what matters.</h2><p>One calendar for everyone in your family.</p></div>
        </aside>
      </div>
    </main>
  )
}
