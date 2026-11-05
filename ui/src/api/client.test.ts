import { AxiosError } from 'axios'
import { afterEach, expect, it } from 'vitest'
import { clearSession, getToken, setSession } from '../auth/storage'
import { api } from './client'

afterEach(() => {
  clearSession()
  window.history.replaceState({}, '', '/')
})

it('attaches the session token to protected requests', async () => {
  setSession('issued-token', 'family@example.com')
  let authorization = ''
  await api.get('/family-members', { adapter: async config => {
    authorization = String(config.headers.Authorization)
    return { data: [], status: 200, statusText: 'OK', headers: {}, config }
  } })
  expect(authorization).toBe('Bearer issued-token')
})

it('clears the session after a protected API response returns 401', async () => {
  window.history.replaceState({}, '', '/calendar?view=month')
  setSession('expired-token', 'family@example.com')
  await expect(api.get('/family-members', { adapter: async config => {
    throw new AxiosError('Unauthorized', 'ERR_BAD_RESPONSE', config, undefined,
      { data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config })
  } })).rejects.toThrow('Unauthorized')
  expect(getToken()).toBeNull()
})
