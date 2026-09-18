import axios from 'axios'
import { AuthenticationApi } from './generated/api/authentication-api.ts'
import { Configuration } from './generated/configuration.ts'

let accessToken = ''
let refreshPromise = null

export const api = axios.create({
  baseURL: '',
  timeout: 10_000,
  withCredentials: true
})

export const generatedConfiguration = new Configuration({ basePath: '' })

export function setAccessToken(token) { accessToken = token || '' }
export function getAccessToken() { return accessToken }
export function clearAccessToken() { accessToken = '' }

api.interceptors.request.use(config => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  return config
})

api.interceptors.response.use(response => response, async error => {
  const config = error.config
  const isAuthRequest = String(config?.url || '').includes('/api/v1/auth/')
  if (error.response?.status !== 401 || config?._retry || isAuthRequest) {
    if (error.response?.data?.code) error.problem = error.response.data
    throw error
  }
  config._retry = true
  try {
    refreshPromise ||= new AuthenticationApi(generatedConfiguration, '', api)
      .refreshAccessToken({ refreshRequest: {} })
      .then(response => {
        const token = response.data?.data?.accessToken || ''
        setAccessToken(token)
        return token
      })
      .finally(() => { refreshPromise = null })
    await refreshPromise
    return api(config)
  } catch (refreshError) {
    clearAccessToken()
    throw refreshError
  }
})
