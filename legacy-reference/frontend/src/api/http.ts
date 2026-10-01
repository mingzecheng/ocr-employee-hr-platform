import axios from 'axios'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 20_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('hr.access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.dispatchEvent(new Event('hr:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export function unwrap<T>(payload: { data: { code: string; message: string; data: T | null } }): T {
  const envelope = payload.data
  if (envelope.code !== '0' || envelope.data === null) {
    throw new Error(envelope.message || '请求失败')
  }
  return envelope.data
}
