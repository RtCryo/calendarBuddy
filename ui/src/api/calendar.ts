import { api } from './client'

export const colors = ['indigo', 'teal', 'coral', 'rose', 'amber', 'sky', 'violet', 'slate'] as const
export type Member = { id: number; name: string; color: string }
export type RemovalImpact = { deleteCount: number; unlinkCount: number }
export type Recurrence = { frequency: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'; interval: number; weekdays: string[]; until: string | null; count: number | null }
export type Appointment = {
  appointmentId: number; occurrenceDate: string; seriesId: number | null; recurring: boolean
  title: string; location: string | null; notes: string | null; allDay: boolean
  startAt: string | null; endAt: string | null; startDate: string | null; endDate: string | null
  participantIds: number[]; recurrence: Recurrence | null; version: number
}
export type AppointmentInput = Omit<Appointment, 'appointmentId' | 'occurrenceDate' | 'seriesId' | 'recurring'>

export type Account = { email: string; timeZone: string }

export const accountApi = {
  get: async () => (await api.get<Account>('/account')).data,
}

export const familyApi = {
  list: async () => (await api.get<Member[]>('/family-members')).data,
  create: async (member: Omit<Member, 'id'>) => (await api.post<Member>('/family-members', member)).data,
  update: async (id: number, member: Omit<Member, 'id'>) => (await api.put<Member>(`/family-members/${id}`, member)).data,
  impact: async (id: number) => (await api.get<RemovalImpact>(`/family-members/${id}/removal-impact`)).data,
  remove: async (id: number) => (await api.delete<RemovalImpact>(`/family-members/${id}`)).data,
}

export const calendarApi = {
  range: async (from: string, to: string, memberId?: number) => (await api.get<Appointment[]>('/appointments', { params: { from, to, memberId } })).data,
  get: async (id: number) => (await api.get<Appointment>(`/appointments/${id}`)).data,
  create: async (item: AppointmentInput) => (await api.post<Appointment>('/appointments', item)).data,
  update: async (id: number, item: AppointmentInput, scope?: 'this' | 'all', occurrenceDate?: string) =>
    (await api.put<Appointment>(`/appointments/${id}`, item, { params: { scope, occurrenceDate } })).data,
  remove: async (id: number, version: number, scope?: 'this' | 'all', occurrenceDate?: string) =>
    api.delete(`/appointments/${id}`, { params: { version, scope, occurrenceDate } }),
}
