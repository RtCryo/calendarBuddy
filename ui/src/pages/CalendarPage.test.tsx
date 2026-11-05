import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { calendarApi, familyApi, type Appointment } from '../api/calendar'
import { CalendarPage } from './CalendarPage'

const anna = { id: 1, name: 'Anna', color: 'coral' }
const ben = { id: 2, name: 'Ben', color: 'teal' }

beforeEach(() => { vi.spyOn(familyApi, 'list').mockResolvedValue([anna, ben]) })
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true }) })

const timed = (id: number, title: string, start: string, end: string, extra: Partial<Appointment> = {}): Appointment => ({
  appointmentId: id, occurrenceDate: start.slice(0, 10), seriesId: null, recurring: false, title, location: null, notes: null,
  allDay: false, startAt: `${start}:00+01:00`, endAt: `${end}:00+01:00`, startDate: null, endDate: null,
  participantIds: [1], recurrence: null, version: 3, ...extra,
})
const series = (extra: Partial<Appointment> = {}): Appointment => timed(10, 'Swimming', '2026-11-03T16:00', '2026-11-03T17:00', {
  seriesId: 10, recurring: true, participantIds: [2], version: 4,
  recurrence: { frequency: 'WEEKLY', interval: 1, weekdays: ['TUESDAY'], until: null, count: null }, ...extra })

function Where() { const location = useLocation(); return <div data-testid="where">{location.pathname}{location.search}</div> }
function renderAt(url: string) {
  return render(<MemoryRouter initialEntries={[url]}><Where /><Routes>
    <Route path="/calendar" element={<CalendarPage />} /><Route path="/family" element={<h1>Family page</h1>} />
  </Routes></MemoryRouter>)
}
const where = () => screen.getByTestId('where').textContent

it('keeps view, date, and member in the URL and restores them', async () => {
  const range = vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const first = renderAt('/calendar?view=agenda&date=2026-11-03&member=1')
  expect(await screen.findByRole('heading', { name: "Anna's calendar" })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: 'View' })).toHaveValue('agenda')
  expect(range).toHaveBeenCalledWith('2026-11-03', '2026-12-03', 1)

  await userEvent.click(screen.getByRole('button', { name: 'Next period' }))
  expect(where()).toContain('date=2026-12-03')
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Show' }), '')
  expect(await screen.findByRole('heading', { name: 'Family calendar' })).toBeInTheDocument()
  expect(where()).not.toContain('member=')
  await userEvent.click(screen.getByRole('button', { name: 'Today' }))
  expect(where()).toMatch(/date=\d{4}-\d{2}-\d{2}/)
  first.unmount()

  renderAt('/calendar?view=week&date=2026-11-12&member=2')
  expect(await screen.findByRole('heading', { name: "Ben's calendar" })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: 'View' })).toHaveValue('week')
  expect(screen.getByRole('combobox', { name: 'Show' })).toHaveValue('2')
  expect(range).toHaveBeenLastCalledWith('2026-11-09', '2026-11-16', 2)
})

it('navigates months and weeks and switches views', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  renderAt('/calendar?view=month&date=2026-11-03')
  await screen.findByRole('grid', { name: 'Month calendar' })
  await userEvent.click(screen.getByRole('button', { name: 'Previous period' }))
  expect(where()).toContain('date=2026-10-01')
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'View' }), 'week')
  expect(await screen.findByRole('group', { name: 'Week calendar' })).toBeInTheDocument()
  expect(where()).toContain('view=week')
})

it('shows the month grid with today marked, three chips, and a +N more control', async () => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-11-12T10:00:00Z'))
  const day = (id: number) => timed(id, `Task ${id}`, `2026-11-12T0${id}:00`, `2026-11-12T0${id}:30`)
  vi.spyOn(calendarApi, 'range').mockResolvedValue([1, 2, 3, 4, 5].map(day))
  renderAt('/calendar?view=month&date=2026-11-12')
  const grid = await screen.findByRole('grid', { name: 'Month calendar' })
  expect(within(grid).getAllByRole('columnheader')).toHaveLength(7)
  expect(within(grid).getAllByRole('columnheader')[0]).toHaveTextContent('Monday')
  const today = within(grid).getByRole('button', { name: /Add appointment on Thursday, 12 November 2026 \(today\)/ })
  expect(today.closest('[role=gridcell]')).toHaveAttribute('aria-current', 'date')
  expect(within(grid).getAllByRole('button', { name: /Task \d/ })).toHaveLength(3)

  await userEvent.click(within(grid).getByRole('button', { name: /2 more appointments/ }))
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getAllByRole('button', { name: /Task \d/ })).toHaveLength(5)
})

it('navigates month days with the arrow keys and exposes accessible names', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([timed(1, 'Dentist', '2026-11-04T10:00', '2026-11-04T11:00', { participantIds: [1, 2] })])
  renderAt('/calendar?view=month&date=2026-11-03')
  await screen.findByRole('grid', { name: 'Month calendar' })
  const start = screen.getByRole('button', { name: /Add appointment on Tuesday, 3 November 2026/ })
  start.focus()
  await userEvent.keyboard('{ArrowRight}')
  expect(screen.getByRole('button', { name: /Add appointment on Wednesday, 4 November 2026/ })).toHaveFocus()
  await userEvent.keyboard('{ArrowDown}')
  expect(screen.getByRole('button', { name: /Add appointment on Wednesday, 11 November 2026/ })).toHaveFocus()
  const chip = screen.getByRole('button', { name: 'Wednesday, 4 November 2026, 10:00, Dentist, with Anna and Ben' })
  expect(chip).toHaveTextContent('10:00')
})

it('creates from an empty day with the filtered member preselected', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const create = vi.spyOn(calendarApi, 'create').mockResolvedValue(timed(1, 'x', '2026-11-12T09:00', '2026-11-12T10:00'))
  renderAt('/calendar?view=month&date=2026-11-03&member=2')
  await screen.findByRole('grid', { name: 'Month calendar' })
  const opener = screen.getByRole('button', { name: /Add appointment on Thursday, 12 November 2026/ })
  await userEvent.click(opener)
  const dialog = screen.getByRole('dialog', { name: 'New appointment' })
  expect(within(dialog).getByLabelText('Start')).toHaveValue('2026-11-12T09:00')
  expect(within(dialog).getByRole('checkbox', { name: /Ben/ })).toBeChecked()
  expect(within(dialog).getByRole('checkbox', { name: /Anna/ })).not.toBeChecked()
  await userEvent.type(within(dialog).getByRole('textbox', { name: 'Title' }), 'Football')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
  await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({
    title: 'Football', participantIds: [2], startAt: '2026-11-12T08:00:00.000Z', endAt: '2026-11-12T09:00:00.000Z' })))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

it('shows the week view with an all-day row, 07:00 scroll position, side-by-side overlaps, and slot creation', async () => {
  const allDay = timed(3, 'Holiday', '2026-11-03T00:00', '2026-11-03T00:00', { allDay: true, startAt: null, endAt: null, startDate: '2026-11-03', endDate: '2026-11-03' })
  vi.spyOn(calendarApi, 'range').mockResolvedValue([
    timed(1, 'Event 1', '2026-11-03T09:00', '2026-11-03T11:00'), timed(2, 'Event 2', '2026-11-03T09:30', '2026-11-03T10:30'), allDay])
  const { container } = renderAt('/calendar?view=week&date=2026-11-03')
  await screen.findByRole('group', { name: 'Week calendar' })
  expect(screen.getByRole('region', { name: /Hourly schedule/ }).scrollTop).toBe(7 * 62)
  expect(within(screen.getByRole('row', { name: /All day/ })).getByRole('button', { name: /Holiday, All day|All day, Holiday/ })).toBeInTheDocument()
  expect([...container.querySelectorAll<HTMLElement>('.week-event-position')].map(node => node.style.width)).toEqual(['50%', '50%'])
  await userEvent.click(screen.getByRole('button', { name: 'Add appointment on Wednesday, 4 November 2026 at 14:00' }))
  expect(screen.getByLabelText('Start')).toHaveValue('2026-11-04T14:00')
})

it('groups the agenda by day and is the default on narrow viewports', async () => {
  Object.defineProperty(window, 'innerWidth', { value: 600, configurable: true })
  vi.spyOn(calendarApi, 'range').mockResolvedValue([
    timed(1, 'Piano', '2026-11-03T15:00', '2026-11-03T16:00'), timed(2, 'Choir', '2026-11-03T18:00', '2026-11-03T19:00'),
    timed(3, 'Doctor', '2026-11-05T08:00', '2026-11-05T09:00')])
  renderAt('/calendar?date=2026-11-03')
  const agenda = await screen.findByRole('list', { name: 'Agenda' })
  expect(screen.getByRole('combobox', { name: 'View' })).toHaveValue('agenda')
  const days = within(agenda).getAllByRole('listitem')
  expect(days).toHaveLength(2)
  expect(within(days[0]).getAllByRole('button')).toHaveLength(2)
  expect(within(days[1]).getByRole('button', { name: /Doctor/ })).toBeInTheDocument()
})

it('explains an empty period without treating it as an error and offers to add', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  renderAt('/calendar?view=month&date=2026-11-03')
  expect(await screen.findByRole('heading', { name: 'No appointments in this period' })).toBeInTheDocument()
  expect(screen.getByRole('grid', { name: 'Month calendar' })).toBeInTheDocument()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Add appointment' }))
  expect(screen.getByRole('dialog', { name: 'New appointment' })).toBeInTheDocument()
})

it('shows loading and a retryable error state', async () => {
  const range = vi.spyOn(calendarApi, 'range').mockRejectedValueOnce(new Error('offline')).mockResolvedValue([])
  renderAt('/calendar?view=month&date=2026-11-03')
  expect(screen.getByRole('status')).toHaveTextContent('Loading appointments')
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
  await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByRole('grid', { name: 'Month calendar' })).toBeInTheDocument()
  expect(range).toHaveBeenCalledTimes(2)
})

it('directs a family without members to add the first member', async () => {
  vi.mocked(familyApi.list).mockResolvedValue([])
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  renderAt('/calendar?view=month&date=2026-11-03')
  const notice = await screen.findByText(/Add a family member before planning/)
  expect(notice).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'New appointment' })).toBeDisabled()
  await userEvent.click(screen.getByRole('link', { name: 'Go to Family' }))
  expect(await screen.findByRole('heading', { name: 'Family page' })).toBeInTheDocument()
})

it('shows details, validates the editor without sending a request, and returns focus', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([timed(1, 'Piano', '2026-11-03T15:00', '2026-11-03T16:00', { location: 'Music school', notes: 'Bring sheets' })])
  const update = vi.spyOn(calendarApi, 'update')
  renderAt('/calendar?view=agenda&date=2026-11-03')
  const opener = await screen.findByRole('button', { name: /Piano/ })
  await userEvent.click(opener)
  const dialog = screen.getByRole('dialog', { name: 'Appointment details' })
  expect(within(dialog).getByText('Music school')).toBeInTheDocument()
  expect(within(dialog).getByText('Bring sheets')).toBeInTheDocument()
  expect(within(dialog).getByText('Anna')).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole('button', { name: 'Edit' }))
  const editor = screen.getByRole('dialog', { name: 'Edit appointment' })
  await userEvent.clear(within(editor).getByRole('textbox', { name: 'Title' }))
  await userEvent.clear(within(editor).getByLabelText('End'))
  await userEvent.type(within(editor).getByLabelText('End'), '2026-11-03T14:00')
  await userEvent.click(within(editor).getByRole('checkbox', { name: /Anna/ }))
  await userEvent.click(within(editor).getByRole('button', { name: 'Save' }))
  expect(within(editor).getByText('Enter a title.')).toBeInTheDocument()
  expect(within(editor).getByText('The end cannot be before the start.')).toBeInTheDocument()
  expect(within(editor).getByText('Choose at least one participant.')).toBeInTheDocument()
  expect(within(editor).getByRole('textbox', { name: 'Title' })).toBeInvalid()
  expect(update).not.toHaveBeenCalled()
  await userEvent.click(within(editor).getByRole('button', { name: 'Cancel' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }))
  await waitFor(() => expect(opener).toHaveFocus())
})

it('confirms deletion, refreshes the calendar, and moves focus to a sensible place', async () => {
  const range = vi.spyOn(calendarApi, 'range').mockResolvedValueOnce([timed(1, 'Piano', '2026-11-03T15:00', '2026-11-03T16:00')]).mockResolvedValue([])
  const remove = vi.spyOn(calendarApi, 'remove').mockResolvedValue({} as never)
  renderAt('/calendar?view=agenda&date=2026-11-03')
  await userEvent.click(await screen.findByRole('button', { name: /Piano/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
  expect(remove).not.toHaveBeenCalled()
  expect(screen.getByText(/This cannot be undone/)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Delete appointment' }))
  await waitFor(() => expect(remove).toHaveBeenCalledWith(1, 3))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(range).toHaveBeenCalledTimes(2)
  expect(screen.queryByRole('button', { name: /Piano/ })).not.toBeInTheDocument()
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Family calendar' })).toHaveFocus())
})

it('explains a conflict and reloads the changed appointment without leaving the calendar', async () => {
  const stale = timed(1, 'Piano', '2026-11-03T15:00', '2026-11-03T16:00')
  const fresh = timed(1, 'Piano lesson', '2026-11-03T15:00', '2026-11-03T16:00', { version: 4 })
  vi.spyOn(calendarApi, 'range').mockResolvedValueOnce([stale]).mockResolvedValue([fresh])
  vi.spyOn(calendarApi, 'update').mockRejectedValue(new AxiosError('conflict', '409', undefined, undefined, { status: 409 } as never))
  renderAt('/calendar?view=agenda&date=2026-11-03&member=1')
  await userEvent.click(await screen.findByRole('button', { name: /Piano/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Edit' }))
  await userEvent.type(screen.getByRole('textbox', { name: 'Title' }), '!')
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('changed on another device')
  await userEvent.click(screen.getByRole('button', { name: 'Reload appointment' }))
  expect(await screen.findByRole('heading', { name: 'Piano lesson' })).toBeInTheDocument()
  expect(where()).toContain('member=1')
})

it('creates a weekly repeat on two weekdays with an end date', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const create = vi.spyOn(calendarApi, 'create').mockResolvedValue(series())
  renderAt('/calendar?view=month&date=2026-11-03')
  await screen.findByRole('grid', { name: 'Month calendar' })
  await userEvent.click(screen.getByRole('button', { name: /Add appointment on Tuesday, 3 November 2026/ }))
  const dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByRole('textbox', { name: 'Title' }), 'Swimming')
  await userEvent.click(within(dialog).getByRole('checkbox', { name: /Ben/ }))
  await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Repeat' }), 'WEEKLY')
  expect(within(dialog).getByRole('checkbox', { name: 'Tuesday' })).toBeChecked()
  await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Thursday' }))
  await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Ends' }), 'until')
  const last = within(dialog).getByLabelText('Last date')
  await userEvent.clear(last)
  await userEvent.type(last, '2026-12-17')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
  await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({
    recurrence: { frequency: 'WEEKLY', interval: 1, weekdays: ['TUESDAY', 'THURSDAY'], until: '2026-12-17', count: null } })))
})

it('validates repeat rules inline', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const create = vi.spyOn(calendarApi, 'create')
  renderAt('/calendar?view=month&date=2026-11-03')
  await screen.findByRole('grid', { name: 'Month calendar' })
  await userEvent.click(screen.getByRole('button', { name: 'New appointment' }))
  const dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByRole('textbox', { name: 'Title' }), 'X')
  await userEvent.click(within(dialog).getByRole('checkbox', { name: /Anna/ }))
  await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Repeat' }), 'WEEKLY')
  await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Tuesday' }))
  await userEvent.clear(within(dialog).getByLabelText(/^Every/))
  await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Ends' }), 'count')
  await userEvent.clear(within(dialog).getByLabelText('Number of occurrences'))
  await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }))
  expect(within(dialog).getByText('Repeat every 1 to 99 times.')).toBeInTheDocument()
  expect(within(dialog).getByText('Choose at least one weekday.')).toBeInTheDocument()
  expect(within(dialog).getByText('Enter 1 to 730 occurrences.')).toBeInTheDocument()
  expect(create).not.toHaveBeenCalled()
})

async function openSeriesEditor() {
  const occurrence = series({ occurrenceDate: '2026-11-17', startAt: '2026-11-17T16:00:00+01:00', endAt: '2026-11-17T17:00:00+01:00' })
  vi.spyOn(calendarApi, 'range').mockResolvedValue([occurrence])
  vi.spyOn(calendarApi, 'get').mockResolvedValue(series())
  renderAt('/calendar?view=agenda&date=2026-11-03')
  await userEvent.click(await screen.findByRole('button', { name: /Swimming/ }))
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getByText(/Repeats every week/)).toBeInTheDocument()
  await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Edit' })).toBeEnabled())
  await userEvent.click(within(dialog).getByRole('button', { name: 'Edit' }))
}

it('marks occurrences with a repeat indicator in chips and lets the user cancel the scope choice', async () => {
  const update = vi.spyOn(calendarApi, 'update')
  await openSeriesEditor()
  await userEvent.clear(screen.getByRole('textbox', { name: 'Title' }))
  await userEvent.type(screen.getByRole('textbox', { name: 'Title' }), 'Swim training')
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  expect(screen.getByText(/Which appointments should the change apply to/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'This occurrence' })).toHaveFocus()
  await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(update).not.toHaveBeenCalled()
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Swim training')
})

it('edits a single occurrence with the series version', async () => {
  await openSeriesEditor()
  const update = vi.spyOn(calendarApi, 'update').mockResolvedValue(series())
  const start = screen.getByLabelText('Start')
  await userEvent.clear(start)
  await userEvent.type(start, '2026-11-17T17:00')
  const end = screen.getByLabelText('End')
  await userEvent.clear(end)
  await userEvent.type(end, '2026-11-17T18:00')
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  await userEvent.click(screen.getByRole('button', { name: 'This occurrence' }))
  await waitFor(() => expect(update).toHaveBeenCalledWith(10, expect.objectContaining({
    recurrence: null, version: 4, startAt: '2026-11-17T16:00:00.000Z', endAt: '2026-11-17T17:00:00.000Z' }), 'this', '2026-11-17'))
})

it('renames the whole series without moving its start', async () => {
  await openSeriesEditor()
  const update = vi.spyOn(calendarApi, 'update').mockResolvedValue(series())
  await userEvent.clear(screen.getByRole('textbox', { name: 'Title' }))
  await userEvent.type(screen.getByRole('textbox', { name: 'Title' }), 'Swim training')
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  await userEvent.click(screen.getByRole('button', { name: 'All occurrences' }))
  await waitFor(() => expect(update).toHaveBeenCalledWith(10, expect.objectContaining({
    title: 'Swim training', startAt: '2026-11-03T15:00:00.000Z', endAt: '2026-11-03T16:00:00.000Z', version: 4,
    recurrence: expect.objectContaining({ frequency: 'WEEKLY' }) }), 'all'))
})

it('warns before a schedule change resets individual changes and saves only after confirmation', async () => {
  await openSeriesEditor()
  const update = vi.spyOn(calendarApi, 'update').mockResolvedValue(series())
  const start = screen.getByLabelText('Start')
  await userEvent.clear(start)
  await userEvent.type(start, '2026-11-17T15:00')
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  await userEvent.click(screen.getByRole('button', { name: 'All occurrences' }))
  expect(screen.getByRole('alert')).toHaveTextContent('resets individually changed or deleted occurrences')
  expect(update).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Back to editing' }))
  expect(update).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Save' }))
  await userEvent.click(screen.getByRole('button', { name: 'All occurrences' }))
  await userEvent.click(screen.getByRole('button', { name: 'Save and reset' }))
  await waitFor(() => expect(update).toHaveBeenCalledWith(10, expect.objectContaining({ startAt: '2026-11-03T14:00:00.000Z' }), 'all'))
})

it('deletes one occurrence or the whole series after the user picks the scope', async () => {
  const occurrence = series({ occurrenceDate: '2026-11-17' })
  vi.spyOn(calendarApi, 'range').mockResolvedValue([occurrence])
  vi.spyOn(calendarApi, 'get').mockResolvedValue(series())
  const remove = vi.spyOn(calendarApi, 'remove').mockResolvedValue({} as never)
  renderAt('/calendar?view=agenda&date=2026-11-03')
  await userEvent.click(await screen.findByRole('button', { name: /Swimming/ }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Delete' })).toBeEnabled())
  await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
  await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
  expect(remove).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
  await userEvent.click(screen.getByRole('button', { name: 'Delete this occurrence' }))
  await waitFor(() => expect(remove).toHaveBeenCalledWith(10, 4, 'this', '2026-11-17'))
})

it('shows the repeat selector options and keyboard-operable end choices', async () => {
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  renderAt('/calendar?view=month&date=2026-11-03')
  await screen.findByRole('grid', { name: 'Month calendar' })
  await userEvent.click(screen.getByRole('button', { name: 'New appointment' }))
  const repeat = screen.getByRole('combobox', { name: 'Repeat' })
  expect(within(repeat).getAllByRole('option').map(option => option.textContent)).toEqual(['Does not repeat', 'Daily', 'Weekly', 'Monthly', 'Yearly'])
  for (const frequency of ['DAILY', 'MONTHLY', 'YEARLY']) {
    await userEvent.selectOptions(repeat, frequency)
    expect(screen.queryByRole('group', { name: 'On days' })).not.toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Ends' })).toHaveValue('never')
  }
  await userEvent.selectOptions(repeat, 'WEEKLY')
  expect(screen.getByRole('group', { name: 'On days' })).toBeInTheDocument()
  screen.getByRole('checkbox', { name: 'Tuesday' }).focus()
  await userEvent.keyboard('{Tab}')
  await userEvent.keyboard(' ')
  expect(screen.getByRole('checkbox', { name: 'Wednesday' })).toBeChecked()
})
