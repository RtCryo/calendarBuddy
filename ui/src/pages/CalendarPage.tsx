import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { calendarApi, familyApi, type Appointment, type Member } from '../api/calendar'
import { Dialog } from '../components/Dialog'
import { AppointmentDialog, type DialogTarget } from '../calendar/AppointmentDialog'
import { add, display, monday, parse, shift, today as todayInZone } from '../calendar/dates'
import { eventsOn } from '../calendar/layout'
import { AgendaView, Chip, MonthView, WeekView } from '../calendar/views'

type View = 'month' | 'week' | 'agenda'
const views: View[] = ['month', 'week', 'agenda']

function periodLabel(view: View, date: string, from: string, to: string) {
  if (view === 'month') return display(date, { month: 'long', year: 'numeric' })
  const last = add(to, -1)
  return `${display(from, { day: 'numeric', month: 'long' })} – ${display(last, { day: 'numeric', month: 'long', year: 'numeric' })}`
}

export function CalendarPage() {
  const [params, setParams] = useSearchParams()
  const today = todayInZone()
  const requestedDate = params.get('date') || ''
  const date = /^\d{4}-\d{2}-\d{2}$/.test(requestedDate) && !Number.isNaN(parse(requestedDate).getTime()) ? requestedDate : today
  const requestedView = params.get('view') as View
  const view: View = views.includes(requestedView) ? requestedView : window.innerWidth <= 680 ? 'agenda' : 'month'
  const memberId = Number(params.get('member')) || undefined

  const [members, setMembers] = useState<Member[]>([])
  const [events, setEvents] = useState<Appointment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<DialogTarget | null>(null)
  const [dayList, setDayList] = useState<string | null>(null)
  const latest = useRef(0)

  const monthStart = `${date.slice(0, 7)}-01`
  const nextMonth = add(monthStart, 32).slice(0, 7) + '-01'
  const from = view === 'month' ? monday(monthStart) : view === 'week' ? monday(date) : date
  const to = view === 'month' ? add(monday(add(nextMonth, -1)), 7) : view === 'week' ? add(from, 7) : add(date, 30)
  const days = Array.from({ length: Math.round((parse(to).getTime() - parse(from).getTime()) / 86400000) }, (_, index) => add(from, index))

  const setState = (next: { date?: string; view?: string; member?: string | null }) => {
    const copy = new URLSearchParams(params)
    Object.entries(next).forEach(([key, value]) => value ? copy.set(key, value) : copy.delete(key))
    setParams(copy)
  }

  const load = async () => {
    const ticket = ++latest.current
    const [family, appointments] = await Promise.all([familyApi.list(), calendarApi.range(from, to, memberId)])
    if (ticket !== latest.current) return null
    setMembers(family)
    setEvents(appointments)
    return appointments
  }

  useEffect(() => {
    Promise.resolve().then(() => { setLoading(true); setError('') })
      .then(load)
      .catch(() => setError('The calendar could not be loaded. Try again.'))
      .finally(() => setLoading(false))
    // load is recreated on every render; only the range inputs should retrigger it
  }, [from, to, memberId]) // eslint-disable-line react-hooks/exhaustive-deps

  const refresh = async () => { await load() }
  const reloadOccurrence = async (stale: Appointment) => {
    const fresh = await load()
    return fresh?.find(item => item.appointmentId === stale.appointmentId && item.occurrenceDate === stale.occurrenceDate)
      ?? fresh?.find(item => item.seriesId !== null && item.seriesId === stale.seriesId && item.occurrenceDate === stale.occurrenceDate) ?? null
  }

  const defaultParticipants = memberId ? [memberId] : members.length === 1 ? [members[0].id] : []
  const openNew = (day: string, hour = 9) => setDialog({ kind: 'new', day, hour, participantIds: defaultParticipants })
  const heading = memberId ? `${members.find(member => member.id === memberId)?.name ?? 'Member'}'s calendar` : 'Family calendar'
  const empty = !loading && !error && events.length === 0
  const common = { members, events, today, onOpen: (event: Appointment) => setDialog({ kind: 'event', event }), onCreate: openNew }

  return <>
    <header className="page-heading"><div><h1 tabIndex={-1} data-focus-fallback>{heading}</h1>
      <p>{periodLabel(view, date, from, to)}</p></div>
      <button type="button" className="button button-primary" disabled={members.length === 0} onClick={() => openNew(date)}>New appointment</button></header>

    <div className="calendar-toolbar surface">
      <div className="calendar-nav" role="group" aria-label="Period">
        <button type="button" className="button button-secondary" aria-label="Previous period" onClick={() => setState({ date: shift(date, view, -1) })}>‹</button>
        <button type="button" className="button button-secondary" onClick={() => setState({ date: today })}>Today</button>
        <button type="button" className="button button-secondary" aria-label="Next period" onClick={() => setState({ date: shift(date, view, 1) })}>›</button>
      </div>
      <div className="calendar-filters">
        <label className="field">View
          <select value={view} onChange={event => setState({ view: event.target.value })}>
            <option value="month">Month</option><option value="week">Week</option><option value="agenda">Agenda</option></select></label>
        <label className="field">Show
          <select value={memberId ?? ''} onChange={event => setState({ member: event.target.value || null })}>
            <option value="">Whole family</option>
            {members.map(member => <option key={member.id} value={member.id}>{member.name}</option>)}</select></label>
      </div>
    </div>

    {error && <div role="alert" className="notice notice-error">{error}
      <button type="button" className="button button-secondary" onClick={() => { setLoading(true); setError(''); load().catch(() => setError('The calendar could not be loaded. Try again.')).finally(() => setLoading(false)) }}>Retry</button></div>}
    {!loading && !error && members.length === 0 && <div className="notice notice-info" role="status">
      Add a family member before planning appointments. <Link to="/family">Go to Family</Link></div>}
    {loading && <p role="status">Loading appointments…</p>}

    {!loading && !error && <>
      {view === 'month' && <MonthView {...common} days={days} monthKey={date.slice(0, 7)} onMore={setDayList} onMoveTo={target => setState({ date: target })} />}
      {view === 'week' && <WeekView {...common} days={days} />}
      {view === 'agenda' && (empty ? null : <AgendaView {...common} days={days} />)}
      {empty && <div className="surface empty-state" role="status"><h2>No appointments in this period</h2>
        <p>Nothing is planned for {periodLabel(view, date, from, to)}.</p>
        {members.length > 0 && <button type="button" className="button button-primary" onClick={() => openNew(date)}>Add appointment</button>}</div>}
    </>}

    {dayList && <Dialog labelledBy="day-title" onClose={() => setDayList(null)}>
      <h2 id="day-title">{display(dayList, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</h2>
      <div className="day-list">{eventsOn(events, dayList).map(event => <Chip key={`${event.appointmentId}-${event.occurrenceDate}`} event={event} day={dayList} members={members}
        onOpen={item => { setDayList(null); setDialog({ kind: 'event', event: item }) }} />)}</div>
      <div className="dialog-actions"><button type="button" className="button button-secondary" onClick={() => setDayList(null)}>Close</button></div>
    </Dialog>}

    {dialog && <AppointmentDialog target={dialog} members={members} onClose={() => setDialog(null)} onChanged={refresh} onReload={reloadOccurrence} />}
  </>
}
