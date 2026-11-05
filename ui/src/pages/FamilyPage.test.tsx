import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { calendarApi, familyApi } from '../api/calendar'
import { FamilyPage } from './FamilyPage'

afterEach(() => { cleanup(); vi.restoreAllMocks() })
const anna = { id: 1, name: 'Anna', color: 'coral' }
const ben = { id: 2, name: 'Ben', color: 'teal' }

it('adds a family member with keyboard color selection and confirms removal impact', async () => {
  vi.spyOn(familyApi, 'list').mockResolvedValue([anna])
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const create = vi.spyOn(familyApi, 'create').mockResolvedValue(ben)
  vi.spyOn(familyApi, 'impact').mockResolvedValue({ deleteCount: 2, unlinkCount: 1 })
  const remove = vi.spyOn(familyApi, 'remove').mockResolvedValue({ deleteCount: 2, unlinkCount: 1 })
  render(<FamilyPage />)
  expect(await screen.findByText('Anna')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Add member' }))
  const dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByRole('textbox', { name: 'Name' }), 'Ben')
  within(dialog).getByRole('radio', { name: 'indigo' }).focus()
  await userEvent.keyboard('{ArrowRight}')
  expect(within(dialog).getByRole('radio', { name: 'teal' })).toBeChecked()
  await userEvent.click(within(dialog).getByRole('button', { name: 'Save member' }))
  expect(create).toHaveBeenCalledWith({ name: 'Ben', color: 'teal' })
  await userEvent.click(await screen.findByRole('button', { name: 'Remove Anna' }))
  expect(await screen.findByText(/2 appointments will be deleted and 1 shared appointments/)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Remove member' }))
  expect(remove).toHaveBeenCalledWith(1)
})

it('shows family loading, empty, and error states', async () => {
  let finish!: (value: typeof anna[]) => void
  vi.spyOn(familyApi, 'list').mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    .mockRejectedValueOnce(new Error('offline'))
  vi.spyOn(calendarApi, 'range').mockResolvedValue([])
  const view = render(<FamilyPage />)
  expect(await screen.findByRole('status')).toHaveTextContent('Loading family members')
  finish([])
  expect(await screen.findByRole('heading', { name: 'Your family starts here' })).toBeInTheDocument()
  view.unmount()
  render(<FamilyPage />)
  expect(await screen.findByRole('alert')).toHaveTextContent('could not be loaded')
})
