import axios from 'axios'

const LS_CODE = 'st_access_code'
let accessCode = localStorage.getItem(LS_CODE) || ''

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.request.use(config => {
  if (accessCode) config.headers['X-Access-Code'] = accessCode
  return config
})

export function setAccessCode(code) {
  accessCode = code
  localStorage.setItem(LS_CODE, code)
}

export function getAccessCode() {
  return accessCode
}

export function apiGetData() {
  // axios 响应包了一层 {data, status, ...}，调用方要的是真正的 body
  return api.get('/data').then(r => r.data)
}

export function apiGetHolidays(year) {
  return api.get('/holidays', { params: { year }, timeout: 20000 }).then(r => r.data)
}

export function apiPutAll(payload) {
  return api.put('/data', payload, { timeout: 15000 }).then(r => r.data)
}
