import type { Appointment, AppointmentInput } from '../api/calendar'
import { add } from './dates'

const minutes = (from: string, to: string) => (Date.parse(`${to}:00Z`) - Date.parse(`${from}:00Z`)) / 60000
const addMinutes = (local: string, delta: number) => new Date(Date.parse(`${local}:00Z`) + delta * 60000).toISOString().slice(0, 16)
const days = (from: string, to: string) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000)

/**
 * Applies the changes the user made to one occurrence (original -> draft) to the whole series.
 * Unchanged fields keep the series' own values, so editing the title of the 19 November occurrence
 * never moves the series start. Times are account-zone `YYYY-MM-DDTHH:mm` strings, like the form.
 */
export function applyToSeries(series: Appointment, seriesForm: AppointmentInput, original: AppointmentInput, draft: AppointmentInput): AppointmentInput {
  const result: AppointmentInput = { ...seriesForm, title: draft.title, location: draft.location, notes: draft.notes,
    participantIds: draft.participantIds, recurrence: draft.recurrence, version: series.version }

  if (original.allDay !== draft.allDay) {
    const anchor = (seriesForm.allDay ? seriesForm.startDate! : seriesForm.startAt!.slice(0, 10))
    result.allDay = draft.allDay
    if (draft.allDay) {
      result.startAt = null; result.endAt = null
      result.startDate = anchor
      result.endDate = add(anchor, days(draft.startDate!, draft.endDate!))
    } else {
      result.startDate = null; result.endDate = null
      result.startAt = `${anchor}${draft.startAt!.slice(10)}`
      result.endAt = addMinutes(result.startAt, minutes(draft.startAt!, draft.endAt!))
    }
  } else if (draft.allDay) {
    result.startDate = add(seriesForm.startDate!, days(original.startDate!, draft.startDate!))
    result.endDate = add(seriesForm.endDate!, days(original.endDate!, draft.endDate!))
  } else {
    result.startAt = addMinutes(seriesForm.startAt!, minutes(original.startAt!, draft.startAt!))
    result.endAt = addMinutes(seriesForm.endAt!, minutes(original.endAt!, draft.endAt!))
  }
  return result
}
