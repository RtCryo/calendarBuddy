import { useEffect, useRef } from 'react'
import type { Appointment, Member } from '../api/calendar'
import { MemberBadge } from './MemberMarkers'
import { add, display, fullDate, timeLabel, weekdayNames } from './dates'
import { eventsOn, HOUR_HEIGHT, layoutDay } from './layout'


export type ViewProps = {
  members: Member[]
  events: Appointment[]
  today: string
  onOpen: (event: Appointment) => void
  onCreate: (day: string, hour?: number) => void
}

export function Chip({ event, day, members, onOpen }: { event: Appointment; day: string; members: Member[]; onOpen: (event: Appointment) => void }) {
  const people = members.filter(member => event.participantIds.includes(member.id))
  const name = `${fullDate(day)}, ${timeLabel(event)}, ${event.title}${people.length ? `, with ${people.map(member => member.name).join(' and ')}` : ''}${event.recurring ? ', repeats' : ''}`
  return <button type="button" className="event-chip" aria-label={name} onClick={() => onOpen(event)}>
    <span className="event-time">{timeLabel(event)}</span>{' '}
    <span className="event-title">{event.title}</span>
    {event.recurring && <span className="repeat-indicator" aria-hidden="true"> ↻</span>}
    <span className="event-members" aria-hidden="true">{people.map(member => <MemberBadge key={member.id} member={member} />)}</span>
  </button>
}

export function MonthView({ days, monthKey, events, members, today, onOpen, onCreate, onMore, onMoveTo }: ViewProps & {
  days: string[]; monthKey: string; onMore: (day: string) => void; onMoveTo: (day: string) => void
}) {
  const moveFocus = (domEvent: React.KeyboardEvent<HTMLButtonElement>, day: string) => {
    const delta = ({ ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 } as Record<string, number>)[domEvent.key]
    if (!delta) return
    domEvent.preventDefault()
    const target = add(day, delta)
    const next = document.querySelector<HTMLButtonElement>(`[data-day="${target}"]`)
    if (next) next.focus()
    else onMoveTo(target)
  }
  const weeks = Array.from({ length: days.length / 7 }, (_, index) => days.slice(index * 7, index * 7 + 7))
  return <div className="month-grid surface" role="grid" aria-label="Month calendar">
    <div role="row" className="grid-row">{weekdayNames.map(day => <div key={day} className="weekday-heading" role="columnheader">
      <span aria-hidden="true">{day.slice(0, 3)}</span><span className="sr-only">{day[0] + day.slice(1).toLowerCase()}</span></div>)}</div>
    {weeks.map(week => <div role="row" className="grid-row" key={week[0]}>{week.map(day => {
      const dayEvents = eventsOn(events, day)
      const isToday = day === today
      return <div key={day} role="gridcell" aria-current={isToday ? 'date' : undefined}
        className={`month-day${day.slice(0, 7) !== monthKey ? ' outside' : ''}${isToday ? ' today' : ''}`}>
        <button type="button" className="day-number" data-day={day} onKeyDown={e => moveFocus(e, day)} onClick={() => onCreate(day)}
          aria-label={`Add appointment on ${fullDate(day)}${isToday ? ' (today)' : ''}`}>{Number(day.slice(-2))}</button>
        {dayEvents.slice(0, 3).map(event => <Chip key={`${event.appointmentId}-${event.occurrenceDate}`} event={event} day={day} members={members} onOpen={onOpen} />)}
        {dayEvents.length > 3 && <button type="button" className="more-button" onClick={() => onMore(day)}
          aria-label={`${dayEvents.length - 3} more appointments on ${fullDate(day)}`}>+{dayEvents.length - 3} more</button>}
      </div>
    })}</div>)}
  </div>
}

export function WeekView({ days, events, members, today, onOpen, onCreate }: ViewProps & { days: string[] }) {
  const scroller = useRef<HTMLDivElement>(null)
  useEffect(() => { if (scroller.current) scroller.current.scrollTop = 7 * HOUR_HEIGHT }, [])
  return <div className="week-wrap surface" role="group" aria-label="Week calendar">
    <div className="week-head" role="row"><span role="columnheader">Time</span>
      {days.map(day => <span key={day} role="columnheader" aria-current={day === today ? 'date' : undefined} className={day === today ? 'today' : ''}>
        <button type="button" className="week-day-button" onClick={() => onCreate(day)}
          aria-label={`Add appointment on ${fullDate(day)}${day === today ? ' (today)' : ''}`}>{display(day, { weekday: 'short', day: 'numeric' })}</button></span>)}</div>
    <div className="week-all-day" role="row"><strong role="rowheader">All day</strong>
      {days.map(day => <div key={day} role="gridcell">{eventsOn(events, day).filter(event => event.allDay).map(event =>
        <Chip key={`${event.appointmentId}-${event.occurrenceDate}`} event={event} day={day} members={members} onOpen={onOpen} />)}</div>)}</div>
    <div className="hour-scroll" ref={scroller} tabIndex={0} aria-label="Hourly schedule, scrolls vertically" role="region">
      <div className="hour-grid">{Array.from({ length: 24 }, (_, hour) => <div className="hour-row" key={hour}>
        <span>{String(hour).padStart(2, '0')}:00</span>
        {days.map(day => <div key={day} className="hour-cell"><button type="button" tabIndex={-1}
          aria-label={`Add appointment on ${fullDate(day)} at ${String(hour).padStart(2, '0')}:00`} onClick={() => onCreate(day, hour)} /></div>)}</div>)}</div>
      <div className="week-events-layer">{days.map(day => <div className="week-events-day" key={day}>
        {layoutDay(day, eventsOn(events, day).filter(event => !event.allDay)).map(({ event, start, end, column, columns }) =>
          <div key={`${event.appointmentId}-${event.occurrenceDate}`} className="week-event-position"
            style={{ top: `${start / 60 * HOUR_HEIGHT}px`, height: `${Math.max(28, (end - start) / 60 * HOUR_HEIGHT)}px`, left: `${column / columns * 100}%`, width: `${100 / columns}%` }}>
            <Chip event={event} day={day} members={members} onOpen={onOpen} /></div>)}</div>)}</div>
    </div>
  </div>
}

export function AgendaView({ days, events, members, today, onOpen }: ViewProps & { days: string[] }) {
  const groups = days.map(day => ({ day, items: eventsOn(events, day) })).filter(group => group.items.length > 0)
  return <div className="agenda-list" role="list" aria-label="Agenda">{groups.map(({ day, items }) =>
    <section key={day} role="listitem" className="surface agenda-day" aria-labelledby={`agenda-${day}`}>
      <div className="agenda-day-heading"><h2 id={`agenda-${day}`}>{display(day, { weekday: 'long', day: 'numeric', month: 'long' })}{day === today && ' (today)'}</h2></div>
      {items.map(event => <Chip key={`${event.appointmentId}-${event.occurrenceDate}`} event={event} day={day} members={members} onOpen={onOpen} />)}
    </section>)}</div>
}
