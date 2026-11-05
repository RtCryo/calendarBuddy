import { useEffect, useRef, type ReactNode } from 'react'

const focusable = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'

/**
 * Modal dialog shared by the member and appointment editors. It traps Tab, closes on Escape,
 * and returns focus to the opener (or to the page heading when the opener no longer exists).
 */
export function Dialog({ labelledBy, onClose, className = '', children }: {
  labelledBy: string; onClose: () => void; className?: string; children: ReactNode
}) {
  const card = useRef<HTMLElement>(null)
  const opener = useRef<HTMLElement | null>(document.activeElement as HTMLElement | null)
  const close = useRef(onClose)
  useEffect(() => { close.current = onClose })

  useEffect(() => {
    const node = card.current
    const origin = opener.current
    if (node && !node.contains(document.activeElement)) (node.querySelector<HTMLElement>('[autofocus],input,select,textarea') ?? node).focus()
    return () => {
      setTimeout(() => {
        if (origin?.isConnected) origin.focus()
        else document.querySelector<HTMLElement>('[data-focus-fallback]')?.focus()
      }, 0)
    }
  }, [])

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') { event.stopPropagation(); close.current(); return }
    if (event.key !== 'Tab' || !card.current) return
    const items = Array.from(card.current.querySelectorAll<HTMLElement>(focusable))
    if (items.length === 0) { event.preventDefault(); return }
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && (document.activeElement === first || document.activeElement === card.current)) { event.preventDefault(); last.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
  }

  return <div className="dialog-backdrop" onKeyDown={onKeyDown}>
    <section ref={card} role="dialog" aria-modal="true" aria-labelledby={labelledBy} tabIndex={-1} className={`dialog-card ${className}`.trim()}>
      {children}
    </section>
  </div>
}
