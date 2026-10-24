import axios from 'axios'
import { clearAccessCode, getAccessCode } from '../utils/access'
import { isDemoMode } from '../utils/demo'
import { demoAdapter } from '../demo/adapter'
import { newIdempotencyKey } from '../utils/idempotency'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  ...(isDemoMode ? { adapter: demoAdapter } : {}),
})

apiClient.interceptors.request.use((config) => {
  config.headers.set('X-Trace-Id', newIdempotencyKey())
  const code = getAccessCode()
  if (code && !config.headers.get('X-Access-Code')) {
    config.headers.set('X-Access-Code', code)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const basePath = import.meta.env.BASE_URL.replace(/\/$/, '') || ''
    const lockPath = `${basePath}/lock`
    if (error?.response?.status === 401 && !window.location.pathname.endsWith('/lock')) {
      clearAccessCode()
      window.location.href = lockPath
    }
    return Promise.reject(error)
  },
)

export default apiClient
