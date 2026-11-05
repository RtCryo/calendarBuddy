import type { Appointment, AppointmentInput } from '../api/calendar'

export const ACCOUNT_ZONE = 'Europe/Berlin'
export const weekdayNames = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const

export const iso = (date: Date) => date.toISOString().slice(0, 10)
export const parse = (text: string) => new Date(`${text}T12:00:00Z`)
export const add = (text: string, days: number) => { const date = parse(text); date.setUTCDate(date.getUTCDate() + days); return iso(date) }
export const monday = (text: string) => add(text, -((parse(text).getUTCDay() + 6) % 7))
export const weekdayOf = (text: string) => weekdayNames[(parse(text).getUTCDay() + 6) % 7]
export const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: ACCOUNT_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })
export const display = (text: string, options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', ...options }).format(parse(text))

const weekdayLong = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']
const monthLong = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
/** English long date such as "Tuesday, 3 November 2026", independent of the browser locale. */
export const fullDate = (text: string) => {
  const date = parse(text)
  return `${weekdayLong[date.getUTCDay()]}, ${date.getUTCDate()} ${monthLong[date.getUTCMonth()]} ${date.getUTCFullYear()}`
}

const zoneParts = new Intl.DateTimeFormat('en-CA', { timeZone: ACCOUNT_ZONE, hourCycle: 'h23',
  year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })

function offsetAt(ms: number) {
  const parts = Object.fromEntries(zoneParts.formatToParts(new Date(ms)).map(part => [part.type, part.value]))
  const local = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second)
  return local - Math.floor(ms / 1000) * 1000
}

/** Formats an instant as `YYYY-MM-DDTHH:mm` in the account time zone. */
export function toLocal(value: string | null) {
  if (!value) return ''
  const ms = Date.parse(value)
  return new Date(ms + offsetAt(ms)).toISOString().slice(0, 16)
}

/** Converts an account-zone `YYYY-MM-DDTHH:mm` value into an ISO instant. */
export function toInstant(local: string) {
  const guess = Date.parse(`${local}:00Z`)
  let ms = guess - offsetAt(guess)
  ms = guess - offsetAt(ms)
  return new Date(ms).toISOString()
}

export const isInstant = (value: string | null) => !!value && (value.endsWith('Z') || /[+-]\d\d:\d\d$/.test(value))

export function timeLabel(event: Appointment) {
  return event.allDay ? 'All day' : toLocal(event.startAt).slice(11)
}

export function dateRangeLabel(event: Appointment) {
  if (event.allDay) {
    const from = fullDate(event.startDate!)
    return event.endDate === event.startDate ? `${from}, all day`
      : `${from} to ${fullDate(event.endDate!)}, all day`
  }
  const start = toLocal(event.startAt)
  const end = toLocal(event.endAt)
  const day = display(start.slice(0, 10), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  return start.slice(0, 10) === end.slice(0, 10) ? `${day}, ${start.slice(11)}–${end.slice(11)}`
    : `${day} ${start.slice(11)} to ${display(end.slice(0, 10), { weekday: 'long', day: 'numeric', month: 'long' })} ${end.slice(11)}`
}

export const emptyInput = (date: string, hour = 9): AppointmentInput => ({
  title: '', location: '', notes: '', allDay: false,
  startAt: `${date}T${String(hour).padStart(2, '0')}:00`,
  endAt: hour === 23 ? `${add(date, 1)}T00:00` : `${date}T${String(hour + 1).padStart(2, '0')}:00`,
  startDate: null, endDate: null, participantIds: [], recurrence: null, version: 0,
})

/** Form model: local date-time strings for timed items, dates for all-day items. */
export const toInput = (event: Appointment): AppointmentInput => ({
  title: event.title, location: event.location, notes: event.notes, allDay: event.allDay,
  startAt: event.allDay ? null : toLocal(event.startAt), endAt: event.allDay ? null : toLocal(event.endAt),
  startDate: event.startDate, endDate: event.endDate, participantIds: event.participantIds,
  recurrence: event.recurrence, version: event.version,
})

/** Wire model: converts form-local values to instants. */
export const toPayload = (draft: AppointmentInput): AppointmentInput => ({
  ...draft,
  startAt: draft.allDay ? null : toInstant(draft.startAt!),
  endAt: draft.allDay ? null : toInstant(draft.endAt!),
  startDate: draft.allDay ? draft.startDate : null,
  endDate: draft.allDay ? draft.endDate : null,
  recurrence: draft.recurrence && { ...draft.recurrence,
    weekdays: draft.recurrence.frequency === 'WEEKLY' ? draft.recurrence.weekdays : [] },
})

export function scheduleChanged(before: AppointmentInput, after: AppointmentInput) {
  return before.allDay !== after.allDay || before.startAt !== after.startAt || before.endAt !== after.endAt
    || before.startDate !== after.startDate || before.endDate !== after.endDate
    || JSON.stringify(before.recurrence) !== JSON.stringify(after.recurrence)
}

export function shift(date: string, view: string, direction: number) {
  if (view === 'week') return add(date, direction * 7)
  if (view === 'agenda') return add(date, direction * 30)
  const target = parse(`${date.slice(0, 7)}-01`)
  target.setUTCMonth(target.getUTCMonth() + direction)
  return iso(target)
}

/** Local days (account zone) an occurrence touches, as inclusive date strings. */
export function spanOf(event: Appointment): [string, string] {
  if (event.allDay) return [event.startDate!, event.endDate!]
  const start = toLocal(event.startAt)
  const end = toLocal(event.endAt)
  const lastDay = end.slice(11) === '00:00' && end > start ? add(end.slice(0, 10), -1) : end.slice(0, 10)
  return [start.slice(0, 10), lastDay]
}

export const initials = (name: string) => name.trim().split(/s+/).map(part => part[0]).join('').slice(0, 2).toUpperCase()
