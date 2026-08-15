import axios from 'axios'
import { clearAccessCode, getAccessCode } from '../utils/access'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
})

apiClient.interceptors.request.use((config) => {
  const code = getAccessCode()
  if (code) {
    config.headers.set('X-Access-Code', code)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401 && window.location.pathname !== '/lock') {
      clearAccessCode()
      window.location.href = '/lock'
    }
    return Promise.reject(error)
  },
)

export default apiClient
