import type { AppointmentInput } from '../api/calendar'
import { add, parse } from './dates'

export type Errors = Partial<Record<'title' | 'participants' | 'start' | 'end' | 'interval' | 'weekdays' | 'until' | 'count', string>>

const MINUTE = 60000

/** Mirrors the API rules so obvious mistakes never reach the server. */
export function validate(draft: AppointmentInput): Errors {
  const errors: Errors = {}
  if (!draft.title.trim()) errors.title = 'Enter a title.'
  if (draft.participantIds.length === 0) errors.participants = 'Choose at least one participant.'

  let startDay = ''
  if (draft.allDay) {
    if (!draft.startDate) errors.start = 'Enter a start date.'
    if (!draft.endDate) errors.end = 'Enter an end date.'
    if (draft.startDate && draft.endDate) {
      startDay = draft.startDate
      if (draft.endDate < draft.startDate) errors.end = 'The end date cannot be before the start date.'
      else if (draft.endDate > add(draft.startDate, 30)) errors.end = 'An appointment can last at most 31 days.'
    }
  } else {
    if (!draft.startAt) errors.start = 'Enter a start date and time.'
    if (!draft.endAt) errors.end = 'Enter an end date and time.'
    if (draft.startAt && draft.endAt) {
      startDay = draft.startAt.slice(0, 10)
      const span = (Date.parse(`${draft.endAt}:00Z`) - Date.parse(`${draft.startAt}:00Z`)) / MINUTE
      if (span < 0) errors.end = 'The end cannot be before the start.'
      else if (span > 31 * 24 * 60) errors.end = 'An appointment can last at most 31 days.'
    }
  }

  const rule = draft.recurrence
  if (rule) {
    if (!Number.isInteger(rule.interval) || rule.interval < 1 || rule.interval > 99) errors.interval = 'Repeat every 1 to 99 times.'
    if (rule.frequency === 'WEEKLY' && rule.weekdays.length === 0) errors.weekdays = 'Choose at least one weekday.'
    if (rule.until !== null) {
      if (!rule.until || Number.isNaN(parse(rule.until).getTime())) errors.until = 'Enter the last date.'
      else if (startDay && rule.until < startDay) errors.until = 'The last date cannot be before the start.'
    }
    if (rule.count !== null && (!Number.isInteger(rule.count) || rule.count < 1 || rule.count > 730)) errors.count = 'Enter 1 to 730 occurrences.'
  }
  return errors
}
