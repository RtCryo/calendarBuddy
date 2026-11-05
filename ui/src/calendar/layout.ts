import type { Appointment } from '../api/calendar'
import { spanOf, toLocal } from './dates'

export const HOUR_HEIGHT = 62
/** Minutes a zero-length appointment occupies in the grid so it stays visible and clickable. */
const MIN_MINUTES = 15

export const eventsOn = (events: Appointment[], day: string) => events.filter(event => {
  const [first, last] = spanOf(event)
  return first <= day && last >= day
})

type Placed = { event: Appointment; start: number; end: number; column: number; columns: number }

/** Lays out one day's timed appointments so overlapping ones share the column width. */
export function layoutDay(day: string, events: Appointment[]): Placed[] {
  const minutes = (value: string | null) => {
    const local = toLocal(value)
    if (local.slice(0, 10) < day) return 0
    if (local.slice(0, 10) > day) return 1440
    return Number(local.slice(11, 13)) * 60 + Number(local.slice(14, 16))
  }
  const sorted: Placed[] = events.map(event => {
    const start = minutes(event.startAt)
    const end = minutes(event.endAt)
    // A zero-length item on this day keeps a minimal slot; an item ending at this day's midnight is not on it.
    const shown = end === start && start < 1440 && toLocal(event.startAt).slice(0, 10) === day
      ? Math.min(start + MIN_MINUTES, 1440) : end
    return { event, start, end: shown, column: 0, columns: 1 }
  }).filter(item => item.end > item.start).sort((a, b) => a.start - b.start || a.end - b.end)
  const result: Placed[] = []
  let group: Placed[] = []
  let groupEnd = 0
  const flush = () => {
    const ends: number[] = []
    for (const item of group) {
      let column = ends.findIndex(end => end <= item.start)
      if (column < 0) column = ends.length
      ends[column] = item.end
      item.column = column
    }
    for (const item of group) item.columns = ends.length
    result.push(...group)
    group = []
  }
  for (const item of sorted) {
    if (group.length && item.start >= groupEnd) flush()
    group.push(item)
    groupEnd = Math.max(groupEnd, item.end)
  }
  if (group.length) flush()
  return result
}
