import axios from 'axios'
import { clearSession, getToken } from '../auth/storage'

export const api = axios.create({ baseURL: '/api', headers: { Accept: 'application/json' } })

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token && !config.url?.startsWith('/auth/')) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401
        && window.location.pathname !== '/login'
        && window.location.pathname !== '/register') {
      const path = `${window.location.pathname}${window.location.search}${window.location.hash}`
      clearSession()
      window.location.assign(`/login?next=${encodeURIComponent(path)}`)
    }
    return Promise.reject(error)
  },
)
