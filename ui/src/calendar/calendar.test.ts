import { expect, it } from 'vitest'
import type { Appointment, AppointmentInput } from '../api/calendar'
import { fullDate, initials, setAccountZone, shift, spanOf, toInstant, toLocal } from './dates'
import { applyToSeries } from './series'
import { validate } from './validation'
import { layoutDay } from './layout'

const input = (extra: Partial<AppointmentInput> = {}): AppointmentInput => ({ title: 'Swimming', location: null, notes: null, allDay: false,
  startAt: '2026-11-03T16:00', endAt: '2026-11-03T17:00', startDate: null, endDate: null, participantIds: [1], recurrence: null, version: 1, ...extra })

it('converts between account-zone local times and instants across daylight-saving changes', () => {
  expect(toInstant('2026-07-01T10:00')).toBe('2026-07-01T08:00:00.000Z')
  expect(toInstant('2026-11-03T10:00')).toBe('2026-11-03T09:00:00.000Z')
  expect(toLocal('2026-10-24T08:00:00Z')).toBe('2026-10-24T10:00')
  expect(toLocal('2026-10-26T09:00:00Z')).toBe('2026-10-26T10:00')
  expect(toLocal('2026-03-29T00:30:00Z')).toBe('2026-03-29T01:30')
  expect(toLocal(toInstant('2026-03-29T03:30'))).toBe('2026-03-29T03:30')
})

it('formats English dates independent of the browser locale', () => {
  expect(fullDate('2026-11-03')).toBe('Tuesday, 3 November 2026')
  expect(shift('2026-01-31', 'month', 1)).toBe('2026-02-01')
  expect(shift('2026-11-03', 'week', -1)).toBe('2026-10-27')
})

it('treats an appointment ending at midnight as ending on the previous day', () => {
  const event = { allDay: false, startAt: '2026-11-03T22:00:00+01:00', endAt: '2026-11-04T00:00:00+01:00' } as Appointment
  expect(spanOf(event)).toEqual(['2026-11-03', '2026-11-03'])
})

it('validates required fields, date order, duration, and repeat rules', () => {
  expect(validate(input())).toEqual({})
  expect(validate(input({ title: '  ', participantIds: [] }))).toEqual({ title: 'Enter a title.', participants: 'Choose at least one participant.' })
  expect(validate(input({ endAt: '2026-11-03T15:00' })).end).toMatch(/cannot be before/)
  expect(validate(input({ endAt: '2026-12-10T15:00' })).end).toMatch(/at most 31 days/)
  expect(validate(input({ allDay: true, startAt: null, endAt: null, startDate: '2026-11-03', endDate: '2026-11-02' })).end).toMatch(/cannot be before/)
  const rule = { frequency: 'WEEKLY' as const, interval: 1, weekdays: ['TUESDAY'], until: null, count: null }
  expect(validate(input({ recurrence: rule }))).toEqual({})
  expect(validate(input({ recurrence: { ...rule, interval: 0 } })).interval).toBeDefined()
  expect(validate(input({ recurrence: { ...rule, interval: 100 } })).interval).toBeDefined()
  expect(validate(input({ recurrence: { ...rule, weekdays: [] } })).weekdays).toBeDefined()
  expect(validate(input({ recurrence: { ...rule, until: '2026-11-02' } })).until).toBeDefined()
  expect(validate(input({ recurrence: { ...rule, count: 731 } })).count).toBeDefined()
  expect(validate(input({ recurrence: { ...rule, count: 730 } }))).toEqual({})
})

const series = { version: 4 } as Appointment
const seriesForm = input({ startAt: '2026-11-03T16:00', endAt: '2026-11-03T17:00', version: 4,
  recurrence: { frequency: 'WEEKLY', interval: 1, weekdays: ['TUESDAY'], until: null, count: null } })

it('applies only the changed fields of an occurrence to its series', () => {
  const original = input({ startAt: '2026-11-17T16:00', endAt: '2026-11-17T17:00', recurrence: seriesForm.recurrence })
  const renamed = applyToSeries(series, seriesForm, original, { ...original, title: 'Swim training' })
  expect(renamed).toMatchObject({ title: 'Swim training', startAt: '2026-11-03T16:00', endAt: '2026-11-03T17:00', version: 4 })

  const later = applyToSeries(series, seriesForm, original, { ...original, startAt: '2026-11-17T15:00' })
  expect(later).toMatchObject({ startAt: '2026-11-03T15:00', endAt: '2026-11-03T17:00' })

  const longer = applyToSeries(series, seriesForm, original, { ...original, endAt: '2026-11-17T18:30' })
  expect(longer).toMatchObject({ startAt: '2026-11-03T16:00', endAt: '2026-11-03T18:30' })
})

it('converts between timed and all-day when applying to the series', () => {
  const original = input({ startAt: '2026-11-17T16:00', endAt: '2026-11-17T17:00' })
  const allDay = applyToSeries(series, seriesForm, original,
    { ...original, allDay: true, startAt: null, endAt: null, startDate: '2026-11-17', endDate: '2026-11-18' })
  expect(allDay).toMatchObject({ allDay: true, startAt: null, startDate: '2026-11-03', endDate: '2026-11-04' })
})

it('lays out overlapping appointments side by side and keeps separate groups full width', () => {
  const event = (id: number, start: string, end: string) => ({ appointmentId: id, startAt: `2026-11-03T${start}:00+01:00`, endAt: `2026-11-03T${end}:00+01:00` }) as Appointment
  const placed = layoutDay('2026-11-03', [event(1, '09:00', '11:00'), event(2, '09:30', '10:30'), event(3, '10:15', '11:30'), event(4, '13:00', '14:00')])
  expect(placed.map(item => [item.event.appointmentId, item.column, item.columns])).toEqual([[1, 0, 3], [2, 1, 3], [3, 2, 3], [4, 0, 1]])
})

it('keeps zero-length appointments visible and leaves out items ending at the day start', () => {
  const event = (id: number, start: string, end: string) => ({ appointmentId: id, startAt: `${start}:00+01:00`, endAt: `${end}:00+01:00` }) as Appointment
  const placed = layoutDay('2026-11-03', [event(1, '2026-11-03T09:00', '2026-11-03T09:00'), event(2, '2026-11-02T22:00', '2026-11-03T00:00')])
  expect(placed.map(item => [item.event.appointmentId, item.start, item.end])).toEqual([[1, 540, 555]])
})

it('builds initials from whitespace-separated name parts', () => {
  expect(initials('Susan')).toBe('S')
  expect(initials('Jessica  Brown')).toBe('JB')
})

it('converts times in the time zone reported for the account', () => {
  try {
    setAccountZone('America/New_York')
    expect(toLocal('2026-11-03T15:00:00Z')).toBe('2026-11-03T10:00')
    expect(toInstant('2026-11-03T10:00')).toBe('2026-11-03T15:00:00.000Z')
  } finally { setAccountZone('Europe/Berlin') }
})
